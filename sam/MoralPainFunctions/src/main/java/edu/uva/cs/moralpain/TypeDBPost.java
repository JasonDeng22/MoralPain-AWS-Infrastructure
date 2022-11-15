package edu.uva.cs.moralpain;

import edu.uva.cs.moralpain.VariableManager;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import java.util.ArrayList;

import com.vaticle.typedb.client.TypeDB;
import com.vaticle.typedb.client.api.TypeDBClient;
import com.vaticle.typedb.client.api.TypeDBSession;
import com.vaticle.typedb.client.api.TypeDBTransaction;
import com.vaticle.typedb.client.api.answer.ConceptMap;
import com.vaticle.typeql.lang.TypeQL;
import com.vaticle.typeql.lang.query.TypeQLInsert;
import static com.vaticle.typeql.lang.TypeQL.var;

public class TypeDBPost implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    JSONObject obj = new JSONObject();
    List<JSONObject> reports = new ArrayList<JSONObject>();
    JSONParser parser = new JSONParser();

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();

        // headers.put("Content-Type", "application/json");
        // headers.put("X-Custom-Header", "application/json");

        APIGatewayProxyResponseEvent response = new APIGatewayProxyResponseEvent().withHeaders(headers);

        if (!isValidEvent(input)) {
            context.getLogger().log("invalid event");
            response.setStatusCode(500);
            return response;
        }

        VariableManager variableManager = new VariableManager();
        if (!isValidEnvironment(variableManager)) {
            context.getLogger().log("invalid environment");
            response.setStatusCode(500);
            return response;
        }

        String body = input.getBody(); // get the body value from input request event, which should be JSON string

        try {
            // convert the json input string to JSONObject and retrieve values
            JSONObject jsonBodyObject = (JSONObject) parser.parse(body);
            String id = (String) jsonBodyObject.get("id");
            @SuppressWarnings("unchecked")
            List<String> selections = (List<String>) jsonBodyObject.get("selections");
            Long score_long = (Long) jsonBodyObject.get("score");
            int score = score_long.intValue();
            // date comes in as UNIX time, so using Long is more appropriate
            Long timestamp = (Long) jsonBodyObject.get("timestamp");

            String ip = String.format("%s:1729", variableManager.get("EC2_IP_ADDRESS"));
            TypeDBClient client = TypeDB.coreClient(ip);

            // open up a session
            try (TypeDBSession session = client.session(variableManager.get("DATABASE_NAME"),
                    TypeDBSession.Type.DATA)) {
                // write transaction
                try (TypeDBTransaction writeTransaction = session.transaction(TypeDBTransaction.Type.WRITE)) {

                    // Insert a report using a WRITE transaction
                    var report = var("r").isa("report").has("id", id).has("score", score).has("timestamp",
                            timestamp);

                    // loop over the selections to iteratively add "has" statements to our insert
                    // query
                    for (String selection : selections) {
                        report = report.has("selection", selection);
                    }

                    TypeQLInsert insertQuery = TypeQL.insert(report); // executes insert query

                    List<Object> insertedId = writeTransaction.query().insert(insertQuery).collect(Collectors.toList());
                    System.out.println(
                            "Inserted a report with ID: "
                                    + ((ConceptMap) insertedId.get(0)).get("r").asThing().getIID());

                    // to persist changes, a write transaction must always be committed (closed)
                    writeTransaction.commit();
                }
            }
            body = "";
            return response.withStatusCode(200).withBody(" OK");
        } catch (ParseException e) {
            e.printStackTrace();
            return response.withStatusCode(400).withBody(e.getMessage());
        }

    }

    private boolean isValidEvent(APIGatewayProxyRequestEvent event) {
        return !(event == null || event.getBody() == null || event.getBody().isEmpty());
    }

    private boolean isValidEnvironment(VariableManager variableManager) {
        return variableManager.containsKey("EC2_IP_ADDRESS")
                && !variableManager.getOrDefault("EC2_IP_ADDRESS", "").isEmpty();
    }

}

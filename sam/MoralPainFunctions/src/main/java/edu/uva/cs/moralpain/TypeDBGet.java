package edu.uva.cs.moralpain;

import org.json.*;

import static com.vaticle.typeql.lang.TypeQL.var;

import edu.uva.cs.moralpain.VariableManager;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.stream.Stream;
import java.util.ArrayList;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyRequestEvent;
import com.amazonaws.services.lambda.runtime.events.APIGatewayProxyResponseEvent;

import com.vaticle.typedb.client.TypeDB;
import com.vaticle.typedb.client.api.TypeDBClient;
import com.vaticle.typedb.client.api.TypeDBSession;
import com.vaticle.typedb.client.api.TypeDBTransaction;
import com.vaticle.typedb.client.api.answer.ConceptMap;
import com.vaticle.typeql.lang.TypeQL;
import com.vaticle.typedb.client.api.TypeDBOptions;
import com.vaticle.typeql.lang.query.*;

/**
 * Handler for requests to Lambda function.
 */
public class TypeDBGet implements RequestHandler<APIGatewayProxyRequestEvent, APIGatewayProxyResponseEvent> {

    JSONObject obj = new JSONObject();
    JSONArray reports = new JSONArray();
    List<String> selections = new ArrayList<String>();
    JSONArray selectionsJSON = new JSONArray();

    String previousIID = "";
    String previousID = "";
    Long previousScore = 0L;
    Long previousTimestamp = 0L;

    public APIGatewayProxyResponseEvent handleRequest(final APIGatewayProxyRequestEvent input, final Context context) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Type", "application/json");
        headers.put("X-Custom-Header", "application/json");

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

        // for some reason this fixed an extremely strange bug. I think it has to do
        // with the scope of handleRequest vs the TypeDBGet public class?
        obj = new JSONObject();
        selectionsJSON = new JSONArray();
        reports = new JSONArray();
        previousIID = "";
        previousID = "";
        previousScore = 0L;
        previousTimestamp = 0L;
        selections.clear();

        String ip = String.format("%s:1729", variableManager.get("EC2_IP_ADDRESS"));

        try (TypeDBClient client = TypeDB.coreClient(ip)) {
            // open up a session
            try (TypeDBSession session = client.session(variableManager.get("DATABASE_NAME"),
                    TypeDBSession.Type.DATA)) {
                TypeDBOptions options = TypeDBOptions.core().infer(true); // enable reasoning
                // start a read transaction
                try (TypeDBTransaction readTransaction = session.transaction(TypeDBTransaction.Type.READ, options)) {

                    // "match" and "get" query to retrieve reports and its attributes
                    TypeQLMatch.Filtered getQuery = TypeQL
                            .match(var("r").isa("report").has("selection", var("selections")).has("id",
                                    var("id"))
                                    .has("score", var("s")).has("timestamp",
                                            var("ts")))
                            .get("r", "s", "ts", "id", "selections");

                    Stream<ConceptMap> answers = readTransaction.query().match(getQuery);

                    answers.forEach(answer -> {
                        System.out.println(previousIID + " idk");
                        if (previousIID.equals("")) {
                            previousIID = answer.get("r").asThing().getIID();
                        }

                        if (previousIID.equals(answer.get("r").asThing().getIID())) {

                            previousID = (String) answer.get("id").asAttribute().getValue();
                            previousScore = (Long) answer.get("s").asAttribute().getValue();
                            previousTimestamp = (Long) answer.get("ts").asAttribute().getValue();
                            selections.add((String) answer.get("selections").asAttribute().getValue());

                        } else {
                            obj = new JSONObject();
                            obj.put("timestamp", previousTimestamp);
                            obj.put("id", previousID);
                            obj.put("score", previousScore);

                            for (int i = 0; i < selections.size(); i++) {
                                selectionsJSON.put(selections.get(i));
                            }
                            try {
                                obj.put("selections", selectionsJSON);
                            } catch (JSONException e) {
                                e.printStackTrace();
                            }
                            reports.put(obj);
                            selections.clear();
                            selectionsJSON = new JSONArray();

                            // recompute for the current iteration, which may only have 1 selection
                            previousID = (String) answer.get("id").asAttribute().getValue();
                            previousScore = (Long) answer.get("s").asAttribute().getValue();
                            previousTimestamp = (Long) answer.get("ts").asAttribute().getValue();
                            selections.add((String) answer.get("selections").asAttribute().getValue());
                            previousIID = answer.get("r").asThing().getIID();
                        }

                        System.out.println(selections);
                    });
                    if (previousIID == "") {
                        return response.withStatusCode(200).withBody(" no reports found");
                    } else {
                        obj = new JSONObject();
                        obj.put("timestamp", previousTimestamp);
                        obj.put("id", previousID);
                        obj.put("score", previousScore);

                        for (int i = 0; i < selections.size(); i++) {
                            selectionsJSON.put(selections.get(i));
                        }
                        try {
                            obj.put("selections", selections);
                        } catch (JSONException e) {
                            e.printStackTrace();
                        }
                        reports.put(obj);
                        selections.clear();
                        selectionsJSON = new JSONArray();
                    }
                    System.out.println("reports: " + reports);

                } catch (Exception e) { // autoclosable .close() throws "Exception", I think?
                    context.getLogger().log("transaction error");
                    e.printStackTrace();
                    return response.withStatusCode(400).withBody(e.getMessage());
                }
            } catch (Exception e) {
                context.getLogger().log("session error");
                e.printStackTrace();
                return response.withStatusCode(400).withBody(e.getMessage());
            }
        } catch (Exception e) {
            context.getLogger().log("client error");
            e.printStackTrace();
            return response.withStatusCode(400).withBody(e.getMessage());
        }

        String json = reports.toString();
        // session is closed
        // client.close();
        // client is closed
        return response.withStatusCode(200).withBody(json);
    }

    private boolean isValidEvent(APIGatewayProxyRequestEvent event) {
        return (event != null);
    }

    private boolean isValidEnvironment(VariableManager variableManager) {
        return variableManager.containsKey("EC2_IP_ADDRESS")
                && !variableManager.getOrDefault("EC2_IP_ADDRESS", "").isEmpty();
    }
}

#!/bin/bash
sudo apt install software-properties-common apt-transport-https
sudo apt-key adv --keyserver keyserver.ubuntu.com --recv 8F3DA4B5E9AEF44C
sudo add-apt-repository 'deb [ arch=all ] https://repo.vaticle.com/repository/apt/ trusty main'
sudo apt update
sudo apt-get install typedb-all=2.11.0 typedb-server=2.11.0 typedb-bin=2.9.0
sudo nohup typedb server


# with flags 

#!/bin/bash
sudo apt install software-properties-common apt-transport-https  
sudo apt-key adv --keyserver keyserver.ubuntu.com --recv 8F3DA4B5E9AEF44C
sudo add-apt-repository 'deb [ arch=all ] https://repo.vaticle.com/repository/apt/ trusty main' -y
sudo apt update
sudo apt-get install typedb-all=2.11.0 typedb-server=2.11.0 typedb-bin=2.9.0 -y
sudo nohup typedb server

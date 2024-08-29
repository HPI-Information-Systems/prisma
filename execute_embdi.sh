apt install python3.11 -y

ln -s  /repos/prisma/prisma_data /prisma_data

cd /repos/prisma

git pull

mvn install
mvn compile


cd /repos/prisma/src/main/resources/embdi

poetry env use python3.10

poetry install
poetry run pip install ./embdi

poetry run python embdi_server.py &

sleep 45

mvn exec:java -Dexec.mainClass="de.uni_marburg.schematch.Main" -Dexec.args="--no_name"
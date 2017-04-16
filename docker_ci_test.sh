#!/usr/bin/env bash

docker volume create --name ReportsVolume
ls /var/lib/

./gradlew bootRepackage
cp build/libs/tix-time-condenser-*.jar tix-time-condenser.jar
docker build -f Dockerfile -t tixmeasurements/tix-time-condenser:citest .

docker run -v $PWD/src/test/resources/mock-api-responses.md:/etc/secrets/api.md -p 3000:3000 -dit wolfdeng/api-mock-server

echo 'Sleeping a while, waiting for the mock-server to start'
sleep 30

docker run --net="host" --name="condenser" -e TIX_CONDENSER_TIX_API_PORT=3000 -v ReportsVolume:/tmp/reports -dit tixmeasurements/tix-time-condenser:citest

echo 'Sleeping a while, waiting for the condenser to start'
sleep 60

curl -i -u guest:guest -H "content-type:application/json" -XPOST -d @$PWD/src/test/resources/docker-ci-rabbitmq-data.json http://localhost:15672/api/exchanges/%2f/amq.default/publish

echo 'Sleeping a while, waiting for me to catch up'
sleep 5

python assert_docker_ci.py

exit $?

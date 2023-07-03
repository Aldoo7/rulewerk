#!/bin/bash

# to compile with maven:
# mvn clean install -DskipTests -Pclient

command="java -cp rulewerk-client/target/standalone-rulewerk-client-0.8.0-SNAPSHOT.jar org.semanticweb.rulewerk.client.picocli.Main"
mkdir -p out

for file in $(find /Users/aldo/Downloads/translated-files/ -type f |egrep rules$); do
  fname=$(echo "$file" | rev |cut -d "/" -f1|rev)
  printf '%s\n' "load \"$file\"" "clingo"  |  $command > "out/$fname"
done;


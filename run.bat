rmdir /s output temp
mkdir output

java -jar ResPacker/target/ResPacker-1.0-SNAPSHOT.jar

rmdir /s temp
PAUSE
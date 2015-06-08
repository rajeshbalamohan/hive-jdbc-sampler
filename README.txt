Installation (One time setup):
=============================

1. mvn install:install-file -DgroupId=com.fifesoft -DartifactId=rsyntaxtextarea -Dversion=2.5.1 -Dpackaging=jar -Dfile=./rsyntaxtextarea-2.5.1.jar

2. Download apache-jmeter-2.13.zip and extract to a folder

3. Build the project using "mvn clean build".

4. Copy ./target/hive-jdbc-sampler-1.0-SNAPSHOT.jar to $JMETER_HOME/lib/ext.  Copy ./target/lib/* to $JMETER_HOME/lib/

Starting HiveServer2 with custom port
======================================
1. hive --service hiveserver2 --hiveconf hive.server2.thrift.port=10002

Running jmeter in non-gui mode:
==============================

1. cd $JMETER_HOME/bin

2. Modify same hive.jmx attached here and change based on your needs (edit the script in GUI mode
 in desktop which is lot easier)

3. To run in non-GUI mode, ./jmeter -n -t hive.jmx

4. Pass any hiveconf related items in "additionalConnectionParams" parameter of hive.jmx (e.g ?hive.execution.engine=tez;hive.auto.convert.join.noconditionaltask=false)

5. Example command (where we are trying to run a functional check for PipelinedSorter vs DefaultSorter):
./jmeter -Durl=jdbc:hive2://cn041-10:10002/tpcds5_bin_partitioned_orc_200 -Duser=root -Dpassword= -DadditionalParams="?hive.execution.engine=tez;tez.queue.name=hive1;tez.runtime.io.sort.mb=256;tez.runtime.pipelined-shuffle.enabled=false;" -DcompareWithParams="?hive.execution.engine=tez;tez.queue.name=hive1;tez.runtime.pipelined-shuffle.enabled=false;"

Another example could be to compare vectorization result with non-vectorization result.

6. In case you don't want any comparison, just remove "-DcompareWithParams" from previous example. 

Important Note:
==============
1. "query" argument in jmeter JMX script accepts only one query.  Do not add multiple queries in the same argument (e.g "use mydb; select count(*) from test" is invalid)

Debugging:
=========
1. In case hive job is not starting, check the console logs of hiveserver2 (to verify if the query is getting submitted by jmeter).  Check jmeter.log as well for any exceptions.

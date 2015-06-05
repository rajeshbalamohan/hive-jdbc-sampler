Installation (One time setup):
=============================

1. mvn install:install-file -DgroupId=com.fifesoft -DartifactId=rsyntaxtextarea -Dversion=2.5.1 -Dpackaging=jar -Dfile=./rsyntaxtextarea-2.5.1.jar

2. Download apache-jmeter-2.11.zip and extract to a folder

3. Edit $JMETER_HOME/bin/jmeter and comment out the following line

java $ARGS $JVM_ARGS -jar "`dirname "$0"`/ApacheJMeter.jar" "$@"

4. Add relevant Hadoop/Hive classpath based on your installation.

JMETER_HOME=/grid/0/rajesh/jmeter/apache-jmeter-2.11/
HADOOP_HOME=/grid/0/hadoop
HADOOP_CONF_DIR=/grid/0/hadoopConf
TEZ_HOME=/grid/0/rajesh/tez-autobuild/dist/tez
TEZ_CONF_DIR=/grid/0/rajesh/tez-autobuild/dist/tez/conf
HIVE_HOME=/grid/0/rajesh/tez-autobuild/dist/hive
HIVE_CONF_DIR=/grid/0/rajesh/tez-autobuild/dist/hive/conf

CLASSPATH=$CLASSPATH:$JMETER_HOME/bin/*:$JMETER_HOME/lib/*:$JMETER_HOME/lib/ext/*:$HADOOP_CONF_DIR
:$HADOOP_HOME
/share/hadoop/common/*:$HADOOP_HOME/share/hadoop/common/lib/*:$HIVE_HOME/lib/*:$HIVE_CONF_DIR
:$TEZ_HOME/*:$TEZ_HOME/lib/*:$TEZ_CONF_DIR

echo $CLASSPATH
#java $ARGS $JVM_ARGS -jar "`dirname "$0"`/ApacheJMeter.jar" "$@"
java $ARGS $JVM_ARGS -cp $CLASSPATH org.apache.jmeter.NewDriver "$@"

5. Build the project using "mvn clean build"

6. Copy ./target/hive-jdbc-sampler-1.0-SNAPSHOT.jar to $JMETER_HOME/lib/ext folder

Starting HiveServer2 with custom port
======================================
1. hive --service hiveserver2 --hiveconf hive.server2.thrift.port=10001

Running jmeter in non-gui mode:
==============================

1. cd $JMETER_HOME/bin

2. Modify same hive.jmx attached here and change based on your needs (edit the script in GUI mode
 in desktop which is lot easier)

3. To run in non-GUI mode, ./jmeter -n -t hive.jmx

4. Pass any hiveconf related items in "additionalConnectionParams" parameter of hive.jmx (e.g ?hive.execution.engine=tez;hive.auto.convert.join.noconditionaltask=false)

5. Example command (where we are trying to run a functional check for PipelinedSorter vs DefaultSorter):
./bin/jmeter -Durl=jdbc:hive2://cn041-10:10002/tpcds5_bin_partitioned_orc_200 -Duser=root -Dpassword= -DadditionalParam="?hive.execution.engine=tez;tez.queue.name=hive1;tez.runtime.sorter.class=LEGACY;tez.runtime.pipelined-shuffle.enabled=false;" -DmpareWithParams="?hive.execution.engine=tez;tez.queue.name=hive1;tez.runtime.pipelined-shuffle.enabled=false;"

Another example could be to compare vectorization result with non-vectorization result.

6. In case you don't want any comparison, just remove "-DcompareWithParams" from previous example. 

//TODO: Remove the unwanted arguments in hive.jmx. Except query, rest of the parameters would be populated via system properties.

Important Note:
==============
1. "query" argument in jmeter JMX script accepts only one query.  Do not add multiple queries in the same argument (e.g "use mydb; select count(*) from test" is invalid)

Debugging:
=========
1. In case hive job is not starting, check the console logs of hiveserver2 (to verify if the query is getting submitted by jmeter).  Check jmeter.log as well for any exceptions.

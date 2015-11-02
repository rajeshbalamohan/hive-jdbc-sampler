Installation (One time setup):
=============================

1. mvn install:install-file -DgroupId=com.fifesoft -DartifactId=rsyntaxtextarea -Dversion=2.5.1 -Dpackaging=jar -Dfile=./rsyntaxtextarea-2.5.1.jar

2. Download apache-jmeter-2.13.zip and extract to a folder

3. Build the project using "mvn clean build".

4. Copy ./target/hive-jdbc-sampler-1.0-SNAPSHOT.jar to $JMETER_HOME/lib/ext.  Copy ./target/lib/* to $JMETER_HOME/lib/

5. Copy hive-site.xml and tez-site.xml in $JMETER_HOME/lib directory

6. Optional: Download http://jmeter-plugins.org/downloads/file/ServerAgent-2.2.1
.zip and unzip in all machines which need to be monitored in JMeter. Run "startAgent.sh"

7. Optional: Download http://jmeter-plugins.org/downloads/file/JMeterPlugins-Standard-1.3.0.zip in $JMETER_HOME and unzip it.

Starting HiveServer2 with custom port
======================================
1. hive --service hiveserver2 --hiveconf hive.server2.thrift.port=10002

TPC-DS JMeter script:
====================
1. tpcds-hive.jmx is attached here, which has the details on tpcds queries.
2. Checkout hive-testbench which has TPC-DS and TPC-H queries. Note down the location of TPC-DS query folder.
3. Configure your jmeter command line arguement to point to the tpcds query folder (hive-bench). For more
details on command-line option, refer to the example given in next section.

Running jmeter in non-gui mode:
==============================

1. cd $JMETER_HOME/bin

2. Example command for running tpc-ds
   ./jmeter -DresultPrefix=tpcds-200 -Durl=jdbc:hive2://hs2:10002/tpcds_bin_partitioned_orc_200
   -Duser=root -Dpassword= -DsqlFilesBaseFolder=/tmp/hive-testbench/sample-queries-tpcds/ -DadditionalParams="?hive.cbo.enable=true;hive.execution.engine=tez;tez.queue.name=default;tez.runtime.io.sort.mb=1800;tez.runtime.pipelined-shuffle.enabled=false;tez.session.am.dag.submit.timeout.secs=100;tez.am.session.min.held-containers=0"

   Example command (where we are trying to run a functional check for PipelinedSorter vs DefaultSorter):
   ./jmeter -Durl=jdbc:hive2://cn041-10:10002/tpcds5_bin_partitioned_orc_200 -Duser=root -Dpassword= -DadditionalParams="?hive.execution.engine=tez;tez.queue.name=hive1;tez.runtime.io.sort.mb=256;tez.runtime.pipelined-shuffle.enabled=false;" -DcompareWithParams="?hive.execution.engine=tez;tez.queue.name=default;tez.session.am.dag.submit.timeout.secs=5;"

   Another example could be to compare vectorization result with non-vectorization result.


2. Modify same tpcds-hive.jmx attached here (to suit your test. Default would
 be just run all queries in tpc-ds) and change based
 on your needs (edit the script in GUI mode in desktop which is lot easier).
 Provide "sqlFilesBaseFolder" which serves as the base directory for all
 queries. Now, you can refer to different queries with their names specified
 in "queryFile".

3. To run in non-GUI mode, ./jmeter -n -t tpcds-hive.jmx

4. Pass any hiveconf related items in "additionalConnectionParams" parameter of hive.jmx (e.g ?hive.execution.engine=tez;hive.auto.convert.join.noconditionaltask=false)

5. In case you don't want any comparison, just remove "-DcompareWithParams" from previous example.

Important Note:
==============
1. "query" argument in jmeter points to a single query file. Do not try to add multiple queries in the same file. Create individual query files for different queries. 

Debugging:
=========
1. In case hive job is not starting, check the console logs of hiveserver2 (to verify if the query is getting submitted by jmeter).  Check jmeter.log as well for any exceptions.

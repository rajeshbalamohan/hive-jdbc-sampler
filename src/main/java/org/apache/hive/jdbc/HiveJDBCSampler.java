package org.apache.hive.jdbc;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.Ordering;
import org.apache.commons.io.IOUtils;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jorphan.collections.Data;
import org.apache.jorphan.logging.LoggingManager;
import org.apache.log.Logger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Statement;
import java.sql.Types;
import java.util.Collections;
import java.util.List;

/**
 * <pre>
 * Simple hive JDBC sampler which can be used for
 * 1. Running basic benchmark
 * 2. For validation, wherein you would like to compare the test results of 2 runs for the same
 * queryFile with different parameters.
 *
 * e.g Test whether results are same for pipelinedsorter vs defaultsorter.
 *
 * In such cases, "compareWith_ConnectionParams" can be populated with necessary parameters so
 * that for every queryFile, it would run the normal job & also with the
 * "compareWith_ConnectionParams". It also checks if the results are exactly the same. One caveat
 * is that, if the sample set is too big, one might want to tweak the memory settings of jmeter.
 *
 * Alternatively, large datasets can be populated into another table, which can then be compared
 * by running another queryFile via jmeter.
 *
 * Parameters of interest to be set in system.properties
 * url - JDBC url
 * user - for JDBC
 * password - for JDBC
 * additionalParams - additional configs/parameters to be sent to HiveServer2
 * queryType - select/insert
 * compareWithParams - additional configs/parameters to be tried out for second run of the queryFile.
 * </pre>
 */
public class HiveJDBCSampler extends AbstractJavaSamplerClient implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final Logger LOG = LoggingManager.getLoggerForClass();

  private static final String URL = "url";
  private static final String DEFAULT_DRIVER = "org.apache.hive.jdbc.HiveDriver";
  private static final String USERNAME = "username";
  private static final String PASSWORD = "password";
  private static final String ADDITIONAL_CONN_PARAMS = "additionalParams";
  private static final String SELECT_OR_UPDATE = "queryType";
  private static final String QUERY_FILE = "queryFile";
  private static final String COMPARE_WITH_CONN_PARAMS = "compareWithParams";
  private static final String SQL_FILES_BASE_FOLDER = "sqlFilesBaseFolder";

  private static boolean driverLoaded = false;
  private Connection conn = null;
  private String url;
  private String username;
  private String password;
  private boolean select; //select statement or update statement
  private String queryFile;
  private String query;
  private boolean sessionMode = true; //make 1 connection.
  private JavaSamplerContext context;
  private String compareWithOptions;
  private String sqlFilesBaseFolder;

  private ResultSetMetaData rsmd;

  @Override
  public Arguments getDefaultParameters() {
    Arguments defaultParameters = new Arguments();
    //Populate from system.properties instead
    defaultParameters.addArgument(URL, "${__property(url)}");
    defaultParameters.addArgument(USERNAME, "${__property(user,,root)}");
    defaultParameters.addArgument(PASSWORD, "${__property(password,,)}");
    defaultParameters.addArgument(ADDITIONAL_CONN_PARAMS,
        "${__property(additionalParams,,?hive.execution.engine=tez)}");
    defaultParameters.addArgument(SELECT_OR_UPDATE, "${__property(queryType,,select)}");
    defaultParameters.addArgument(QUERY_FILE,
        "queryFile");
    defaultParameters.addArgument(COMPARE_WITH_CONN_PARAMS, "${__property(compareWithParams,,)}");
    defaultParameters.addArgument(SQL_FILES_BASE_FOLDER, "${__property(sqlFilesBaseFolder,,)}");
    return defaultParameters;
  }

  private String readQuery(String queryFile) {
    StringBuilder query = new StringBuilder();
    FileReader reader = null;
    try {
      reader = new FileReader(new File(sqlFilesBaseFolder, queryFile));
      BufferedReader br = new BufferedReader(reader);
      while(br.ready()) {
        query.append(br.readLine()).append(" ");
      }
    } catch(FileNotFoundException fnf) {
      throw new RuntimeException(fnf);
    } catch (IOException ioe) {
      throw new RuntimeException(ioe);
    } finally {
      IOUtils.closeQuietly(reader);
    }
    String q = query.toString().trim();
    if (q.endsWith(";")) {
      return q.substring(0, q.length()-1);
    } else {
      return q;
    }
  }

  @Override
  public void setupTest(JavaSamplerContext context) {
    try {
      this.context = context;
      this.url = context.getParameter(URL) + context.getParameter(ADDITIONAL_CONN_PARAMS);
      this.password = context.getParameter(USERNAME);
      this.username = context.getParameter(PASSWORD);
      this.select =
          context.getParameter(SELECT_OR_UPDATE, "select").trim().equalsIgnoreCase("select");
      this.queryFile = context.getParameter(QUERY_FILE);
      if (this.queryFile == null || this.queryFile.trim().length() == 0) {
        throw new IllegalArgumentException("Please pass a valid queryFile");
      }
      this.compareWithOptions = context.getParameter(COMPARE_WITH_CONN_PARAMS);
      if (!Strings.isNullOrEmpty(compareWithOptions)) {
        sessionMode = false;
      }
      this.sqlFilesBaseFolder = context.getParameter(SQL_FILES_BASE_FOLDER);
      this.query = readQuery(this.queryFile);
      Preconditions.checkArgument(!Strings.isNullOrEmpty(query), "Please provide valid query for "
          + "testing");

      LOG.info("url:" + url);
      LOG.info("username:" + username);
      LOG.info("password:" + password);
      LOG.info("selectType:" + select);
      LOG.info("queryFile:" + queryFile);
      LOG.info("compareWithOptions:" + compareWithOptions);
      LOG.info("sqlFilesBaseFolder:" + sqlFilesBaseFolder);
      LOG.info("query:" + query);

      initConnection(this.url, this.username, this.password);
    } catch (Exception e) {
      getLogger().error(e.getMessage());
    }
  }

  private void initConnection(String url, String username, String password)
      throws Exception {
    if (!driverLoaded) {
      try {
        Class.forName(DEFAULT_DRIVER);
        System.out.println("Loaded driver : " + DEFAULT_DRIVER);
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
        LOG.error("ClassNotFound ", e);
        System.exit(1);
      }
      driverLoaded = true;
    }
    if (conn == null || conn.isClosed()) {
      LOG.info("Using url : " + url);
      conn = DriverManager.getConnection(url, username, password);
      LOG.info("Got connection");
    }
  }

  private void closeConnection(Connection conn) {
    if (conn != null) {
      try {
        conn.close();
        LOG.info("Closing connection");
      } catch (Exception e) {
        LOG.error("Error in closing connection", e);
      }
    }
  }

  private Result execute(String url, String userName, String password)
      throws Exception {
    initConnection(url, userName, password);
    Statement stmt = conn.createStatement();

    Result result = new Result();

    result.sample = new SampleResult();
    result.sample.sampleStart();

    try {
      if (select) {
        ResultSet rs = stmt.executeQuery(query);
        rsmd = rs.getMetaData();
        result.data = Data.getDataFromResultSet(rs);
        rs.close();
      } else {
        result.rows = stmt.executeUpdate(context.getParameter("queryFile"));
      }

      result.sample.setResponseMessage("\nSuccess");
      result.sample.setSuccessful(true);
    } catch (Exception e) {
      result.sample.setResponseMessage("\nFailed " + Throwables.getStackTraceAsString(e));
      result.sample.setSuccessful(false);
    } finally {
      result.sample.sampleEnd();
      stmt.close();
      if (!sessionMode) {
        closeConnection(conn);
      }
      LOG.info("Finished executing a query");
    }
    return result;
  }

  @Override
  public SampleResult runTest(JavaSamplerContext context) {
    SampleResult sampleResult = new SampleResult();
    sampleResult.sampleStart();
    try {
      Result r = execute(this.url, this.username, this.password);
      sampleResult.addRawSubResult(r.sample);

      //Check if we need to compare with another run
      if (compareWithOptions != null && !compareWithOptions.trim().isEmpty()) {
        String url = context.getParameter("url") + compareWithOptions;
        initConnection(url, this.username, this.password);

        Result newResult = execute(url, this.username, this.password);
        LOG.info("Ran with additional option to be compared with.." + url);

        sampleResult.addRawSubResult(newResult.sample);

        verify(r.data, newResult.data); //compare old data with new data
        Preconditions.checkState(r.rows == newResult.rows, "Rows are not matching r=" + r.rows +
            ", newResult=" + newResult.rows);
      }
      sampleResult.setSuccessful(true);
      //Set the response msg
      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < sampleResult.getSubResults().length; i++) {
        sb.append("\n\t");
        sb.append(i + " th sample time : " + sampleResult.getSubResults()[i].getTime());
      }
      sampleResult.setResponseMessage(sb.toString());
    } catch (Exception e) {
      LOG.error("Exception when running the sampler", e);
      sampleResult.setSuccessful(false);
      sampleResult.setResponseMessage("Error: " + e);

      StringWriter stringWriter = new StringWriter();
      e.printStackTrace(new PrintWriter(stringWriter));
      sampleResult.setResponseData(stringWriter.toString().getBytes());
      sampleResult.setDataType(org.apache.jmeter.samplers.SampleResult.TEXT);
    } finally {
      sampleResult.sampleEnd();
    }
    return sampleResult;
  }

  /**
   * Verify if two datasets are correct.
   *
   * @param d1
   * @param d2
   * @throws Exception
   */
  private void verify(Data d1, Data d2) throws Exception {
    Preconditions.checkState(d1 != null, "d1 Data should not be null");
    Preconditions.checkState(d2 != null, "d2 Data should not be null");

    d1.reset();
    d2.reset();

    if (d1.size() != d2.size()) {
      throw new Exception("d1 size " + d1.size() + " not matching with d2 size " + d2.size());
    }

    String[] col = d1.getHeaders();

    for (int i = 1; i <= col.length; i++) {
      LOG.info("Col type for " + i + " : " + rsmd.getColumnType(i));
    }

    for (int i = 0; i < col.length; i++) {
      List d1Val = d1.getColumnAsObjectArray(col[i]);
      List d2Val = d2.getColumnAsObjectArray(col[i]);
      //can be expensive (but sometimes data can come in out of order. e.g unordered; query_80)
      Collections.sort(d1Val, Ordering.natural().nullsFirst());
      Collections.sort(d2Val, Ordering.natural().nullsFirst());

      if (d1Val == null && d2Val == null) {
        continue;
      }

      if ((d1Val == null && d2Val != null) || (d1Val != null && d2Val == null)) {
        throw new Exception("Please verifiy null checks..d1Val=" + d1Val + "; d2Val=" + d2Val);
      }

      //special case for decimal and float (otherwise can lead to wrong string comparison with
      // precision)
      if (rsmd.getColumnType(i + 1) == Types.FLOAT || rsmd.getColumnType(i + 1) == Types.DECIMAL
          || rsmd.getColumnType(i + 1) == Types.DOUBLE) {
        verifyFloatColumn(d1Val, d2Val, col[i]);
      } else {
        if (!d1Val.equals(d2Val)) {
          throw new Exception(col[i] + " not matching. " + "d1Val=" + d1Val + ", d2Val=" + d2Val);
        } else {
          //LOG.info(col[i] + ". " + "d1Val=" + d1Val + ", d2Val=" + d2Val);
        }
      }
    }
    LOG.info("Data verification complete");
  }

  private void verifyFloatColumn(List d1Val, List d2Val, String column) throws Exception {
    for (int j = 0; j < d1Val.size(); j++) {
      Object v1 = d1Val.get(j);
      Object v2 = d2Val.get(j);
      if (v1 == null && v2 == null) {
        continue;
      }

      if ((v1 == null && v2 != null) || (v1 != null && v2 == null)) {
        throw new Exception("Please verifiy null checks..v1=" + v1 + "; v2=" + v2);
      }

      if (Float.parseFloat(v1.toString()) != Float.parseFloat(v2.toString())) {
        throw new Exception(column + " not matching. " + "d1Val=" + v1 + ", d2Val=" + v2);
      }
    }
  }

  @Override
  public void teardownTest(JavaSamplerContext context) {
    try {
      if (conn != null && !conn.isClosed()) {
        closeConnection(conn);
      }
    } catch (Exception e) {
      LOG.error("Error in closing connection", e);
    }
  }

  static class Result {
    SampleResult sample;
    Data data;
    int rows;
  }
}
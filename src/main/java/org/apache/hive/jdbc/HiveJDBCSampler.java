package org.apache.hive.jdbc;

import java.io.Serializable;
import java.sql.*;
import org.apache.jorphan.collections.Data;
import org.apache.jmeter.protocol.java.sampler.AbstractJavaSamplerClient;
import org.apache.jmeter.config.Arguments;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.protocol.java.sampler.JavaSamplerContext;

public class HiveJDBCSampler extends AbstractJavaSamplerClient implements Serializable {
  private static final long serialVersionUID = 1L;

  private boolean driverLoaded = false;
  Connection conn = null;
  String url;
  String username;
  String password;
  boolean select; //select statement or update statement
  JavaSamplerContext context;

  @Override
  public Arguments getDefaultParameters() {
    Arguments defaultParameters = new Arguments();
    defaultParameters.addArgument("url", "jdbc:hive2://cn041:10000/default");
    defaultParameters.addArgument("driver", "org.apache.hive.jdbc.HiveDriver");
    defaultParameters.addArgument("username", "root");
    defaultParameters.addArgument("password", "");
    defaultParameters.addArgument("additionalConnectionParams", "?hive.execution.engine=tez");
    defaultParameters.addArgument("Select_OR_Update", "select");
    return defaultParameters;
  }


  private Data getDataFromResultSet(ResultSet rs) throws SQLException {
    ResultSetMetaData meta = rs.getMetaData();
    //TODO: if there is too much data, this might throw OOM in jmeter
    //Easier option: rewrite your query to persist large data in temp table
    Data data = new Data();
    int numColumns = meta.getColumnCount();
    String[] dbCols = new String[numColumns];
    for (int i = 0; i < numColumns; i++) {
      dbCols[i] = meta.getColumnName(i + 1);
      data.addHeader(dbCols[i]);
    }
    while (rs.next()) {
      data.next();
      for (int i = 0; i < numColumns; i++) {
        Object o = rs.getObject(i + 1);
        if (o instanceof byte[]) {
          o = new String((byte[]) o);
        }
        data.addColumnValue(dbCols[i], o);
      }
    }
    rs.close();
    return data;
  }

  @Override
  public void setupTest(JavaSamplerContext context) {
    try {
      this.context = context;
      this.url = context.getParameter("url")
          + context.getParameter("additionalConnectionParams");
      this.password = context.getParameter("username");
      this.username = context.getParameter("password");
      this.select = context.getParameter("Select_OR_Update", "select").trim().equalsIgnoreCase
          ("select");
      initConnection();
    } catch(Exception e) {
      getLogger().error(e.getMessage());
    }
  }

  private void initConnection()
      throws Exception {
    if (!driverLoaded) {
      try {
        Class.forName(context.getParameter("driver"));
        System.out.println("Loaded driver : " + context.getParameter("driver"));
      } catch (ClassNotFoundException e) {
        e.printStackTrace();
        System.exit(1);
      }
      driverLoaded = true;
    }
    if (conn == null) {
      conn = DriverManager.getConnection(this.url, this.username, this.password);
      System.out.println("Got connection");
    }
  }

  private void closeConnection(Connection conn) {
    if (conn != null) {
      try {
        conn.close();
        System.out.println("Closing connection");
      } catch(Exception e) {
        e.printStackTrace();
      }
    }
  }

  @Override
  public SampleResult runTest(JavaSamplerContext context) {
    Statement stmt = null;
    SampleResult result = new SampleResult();
    try {
      result.sampleStart();
      stmt = conn.createStatement();
      if (select) {
        ResultSet rs = stmt.executeQuery(context.getParameter("query"));
        Data data = getDataFromResultSet(rs);
        result.setResponseMessage("\nSuccess : " + data.toString());
      } else {
        int rows = stmt.executeUpdate(context.getParameter("query"));
        result.setResponseMessage("\nSuccess : " + rows);
      }
      result.sampleEnd();
      result.setSuccessful(true);
    } catch(Exception e) {
      result.sampleEnd();
      result.setSuccessful( false );
      result.setResponseMessage( "Error: " + e );

      java.io.StringWriter stringWriter = new java.io.StringWriter();
      e.printStackTrace( new java.io.PrintWriter( stringWriter ) );
      result.setResponseData(stringWriter.toString());
      result.setDataType(org.apache.jmeter.samplers.SampleResult.TEXT);
    }
    return result;
  }

  @Override
  public void teardownTest(JavaSamplerContext context) {
    try {
      closeConnection(conn);
    } catch(Exception e) {
      e.printStackTrace();
    }
  }
}
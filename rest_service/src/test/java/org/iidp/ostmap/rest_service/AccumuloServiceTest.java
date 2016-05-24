package org.iidp.ostmap.rest_service;

import org.apache.accumulo.core.client.*;
import org.apache.accumulo.core.client.Scanner;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Mutation;
import org.apache.accumulo.core.data.Range;
import org.apache.accumulo.core.data.Value;
import org.apache.accumulo.core.security.Authorizations;
import org.apache.hadoop.io.Text;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.iidp.ostmap.commons.accumulo.AmcHelper;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AccumuloServiceTest {

    @ClassRule
    public static TemporaryFolder tmpDir = new TemporaryFolder();
    public static TemporaryFolder tmpSettingsDir = new TemporaryFolder();
    public static AmcHelper amc;
    public static File settings;
    private static String tweet1,
            tweet2,
            tweet3;

    private static JSONObject tweet1Json,
            tweet2Json,
            tweet3Json;


    @BeforeClass
    public static void setUpCluster() throws IOException, AccumuloException, TableNotFoundException, TableExistsException, AccumuloSecurityException, JSONException {
        amc = new AmcHelper();

        amc.startMiniCluster(tmpDir.getRoot().getAbsolutePath());

        tmpSettingsDir.create();
        settings = tmpSettingsDir.newFile("settings");

        Connector conn = amc.getConnector();
        System.out.println("I am connected as: " + conn.whoami());

        if(!conn.tableOperations().exists("RawTwitterData")){
            conn.tableOperations().create("RawTwitterData");
        }
        if(!conn.tableOperations().exists("TermIndex")){
            conn.tableOperations().create("TermIndex");
        }

        //write example entry to RawTwitterData

        File f1 = new File(ClassLoader.getSystemClassLoader().getResource("example-tweet-1.json").getFile());
        tweet1 = new String(Files.readAllBytes(f1.toPath()));
        tweet1Json = new JSONObject(tweet1);

        File f2 = new File(ClassLoader.getSystemClassLoader().getResource("example-tweet-2.json").getFile());
        tweet2 = new String(Files.readAllBytes(f2.toPath()));
        tweet2Json = new JSONObject(tweet2);

        File f3 = new File(ClassLoader.getSystemClassLoader().getResource("example-tweet-3.json").getFile());
        tweet3 =  new String(Files.readAllBytes(f3.toPath()));
        tweet3Json = new JSONObject(tweet3);

        ByteBuffer bb1 = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
        bb1.putLong(12345).putInt(123);

        Mutation rawMut1 = new Mutation(bb1.array());
        rawMut1.put("t", "", tweet1);

        ByteBuffer bb2 = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
        bb2.putLong(12347).putInt(678);

        Mutation rawMut2 = new Mutation(bb2.array());
        rawMut2.put("t", "", tweet2);

        ByteBuffer bb3 = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
        bb3.putLong(12349).putInt(679);

        Mutation rawMut3 = new Mutation(bb3.array());
        rawMut3.put("t", "", tweet3);

        System.out.println("keyFormat: " + new String(bb3.array()));

        BatchWriter bw = conn.createBatchWriter("RawTwitterData", new BatchWriterConfig());
        bw.addMutation(rawMut1);
        bw.addMutation(rawMut2);
        bw.addMutation(rawMut3);
        bw.close();

        //write example data to TermIndex
        Mutation m1 = new Mutation("#hashtag");
        m1.put("text".getBytes(), bb1.array(), "2".getBytes());
        Mutation m2 = new Mutation("tralala");
        m2.put("text".getBytes(), bb1.array(), "2".getBytes());
        Mutation m3 = new Mutation("Vollstaendiger");
        m3.put("text".getBytes(), bb2.array(), "2".getBytes());
        Mutation m4 = new Mutation("Tweet");
        m4.put("text".getBytes(), bb2.array(), "2".getBytes());
        Mutation m5 = new Mutation("hund");
        m5.put("text".getBytes(), bb2.array(), "2".getBytes());
        Mutation m55 = new Mutation("maus");
        m55.put("text".getBytes(), bb2.array(), "2".getBytes());
        Mutation m6 = new Mutation("Vollstaendiger");
        m6.put("text".getBytes(), bb3.array(), "2".getBytes());
        Mutation m7 = new Mutation("Tweet");
        m7.put("text".getBytes(), bb3.array(), "2".getBytes());
        Mutation m8 = new Mutation("katze");
        m8.put("text".getBytes(), bb3.array(), "2".getBytes());
        Mutation m9 = new Mutation("#katze");
        m9.put("text".getBytes(), bb3.array(), "2".getBytes());
        Mutation m10 = new Mutation("maus");
        m10.put("text".getBytes(), bb3.array(), "2".getBytes());

        System.out.println(Arrays.toString("text".getBytes()));

        BatchWriter bwti = conn.createBatchWriter("TermIndex", new BatchWriterConfig());
        bwti.addMutation(m1);
        bwti.addMutation(m2);
        bwti.addMutation(m3);
        bwti.addMutation(m4);
        bwti.addMutation(m5);
        bwti.addMutation(m55);
        bwti.addMutation(m6);
        bwti.addMutation(m7);
        bwti.addMutation(m8);
        bwti.addMutation(m9);
        bwti.addMutation(m10);
        bwti.close();

        //create settings file with data of Mini Accumulo Cluster
        FileOutputStream fos = new FileOutputStream(settings, false);
        BufferedWriter br = new BufferedWriter(new OutputStreamWriter(fos));
        String parameters = "accumulo.instance=" + conn.getInstance().getInstanceName() + "\n" +
                "accumulo.user=" + conn.whoami() + "\n" +
                "accumulo.password=password\n" +
                "accumulo.zookeeper=" + conn.getInstance().getZooKeepers();

        System.out.println(parameters);
        br.write(parameters);
        br.flush();
        br.close();
        fos.flush();
        fos.close();


        System.out.println("RawTwitterData: -----------------------------------------------------");
        Scanner s = conn.createScanner("RawTwitterData", new Authorizations("standard"));
        for (Map.Entry<Key, Value> entry : s) {
            System.out.println(entry.getKey() + " | " + entry.getValue());
            //assertEquals(entry.getValue().toString(), testString);
        }
        s.close();
        System.out.println("---------------------------------------------");

    }

    @AfterClass
    public static void shutDownCluster() {

        amc.stopMiniCluster();
    }

    @Test
    public void testReduceIterator() throws AccumuloSecurityException, AccumuloException, TableNotFoundException, IOException, JSONException {
        System.out.println("");
        System.out.println("[testReduceIterator start]");
        AccumuloService as = new AccumuloService();
        as.readConfig(settings.getAbsolutePath());
        ByteBuffer bb3 = ByteBuffer.allocate(Long.BYTES + Integer.BYTES);
        bb3.putLong(12349).putInt(679);
        List<Range> testRange = new ArrayList<>();
        testRange.add(new Range(new Text(bb3.array())));
        BatchScanner bs = as.getRawDataBatchScanner(testRange);
        for (Map.Entry<Key, Value> kv : bs) {
            String value = kv.getValue().toString();
            JSONObject json = new JSONObject(value);
            // needed fields
            assertTrue(json.has("id_str"));
            assertTrue(json.has("created_at"));
            assertTrue(json.has("text"));
            assertTrue(json.has("user"));
            assertTrue(json.has("coordinates"));
            assertTrue(json.has("place"));

            // test for some forbidden fields
            assertFalse(json.has("id"));
            assertFalse(json.has("entities"));
        }
        System.out.println("[testReduceIterator end]");
        System.out.println("");
    }

    @Test
    public void testAccumuloServiceTokenSearch() throws Exception {
        //run Token Search
        System.out.println("settings file path: " + settings.getAbsolutePath());

        String fieldList = "user,text";
        String searchToken = "katze";

        TokenSearchController tsc = new TokenSearchController();
        tsc.set_paramCommaSeparatedFieldList(fieldList);
        tsc.set_paramToken(searchToken);
        tsc.validateQueryParams();
        String result = tsc.getResultsFromAccumulo(settings.getAbsolutePath());
        JSONArray resultArray = new JSONArray(result);

        assertTrue(resultArray.length() == 1);
        assertEquals(tweet3Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));
    }

    @Test
    public void testAccumuloServiceTokenSearchHashtag() throws Exception {
        //run Token Search
        System.out.println("settings file path: " + settings.getAbsolutePath());

        String fieldList = "user,text";
        String searchToken = "#katze";

        TokenSearchController tsc = new TokenSearchController();
        tsc.set_paramCommaSeparatedFieldList(fieldList);
        tsc.set_paramToken(searchToken);
        tsc.validateQueryParams();
        String result = tsc.getResultsFromAccumulo(settings.getAbsolutePath());
        JSONArray resultArray = new JSONArray(result);

        assertTrue(resultArray.length() == 1);
        assertEquals(tweet3Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));
    }

    @Test
    public void testAccumuloServiceTokenSearchWildcard() throws Exception {
        //run Token Search
        System.out.println("settings file path: " + settings.getAbsolutePath());

        String fieldList = "user,text";
        String searchToken = "mau*";

        TokenSearchController tsc = new TokenSearchController();
        tsc.set_paramCommaSeparatedFieldList(fieldList);
        tsc.set_paramToken(searchToken);
        tsc.validateQueryParams();
        String result = tsc.getResultsFromAccumulo(settings.getAbsolutePath());
        JSONArray resultArray = new JSONArray(result);

        assertTrue(resultArray.length() == 2);
        assertEquals(tweet2Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));
        assertEquals(tweet3Json.getString("id_str"),resultArray.getJSONObject(1).getString("id_str"));
    }

     @Test
     public void geoTimeTest() throws Exception{
         System.out.println("settings file path: " + settings.getAbsolutePath());

         GeoTimePeriodController gtpc = new GeoTimePeriodController();

         //wide Time range and wide bounding box to match all 3 tweets
         gtpc.set_paramNorthCoordinate("49");
         gtpc.set_paramEastCoordinate("30");
         gtpc.set_paramSouthCoordinate("40");
         gtpc.set_paramWestCoordinate("0");
         gtpc.set_paramStartTime(Long.toString(12300));
         gtpc.set_paramEndTime(Long.toString(12399));

         String result = gtpc.getResultsFromAccumulo(settings.getAbsolutePath());
         JSONArray resultArray = new JSONArray(result);
         assertTrue(resultArray.length() == 3);

         //short Time range and wide bounding box to match only tweet 1
         gtpc.set_paramStartTime(Long.toString(12340));
         gtpc.set_paramEndTime(Long.toString(12346));

         result = gtpc.getResultsFromAccumulo(settings.getAbsolutePath());
         resultArray = new JSONArray(result);

         assertTrue(resultArray.length() == 1);
         assertEquals(tweet1Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));

         //short Time range and wide bounding box to match only tweet 3
         gtpc.set_paramStartTime(Long.toString(12348));
         gtpc.set_paramEndTime(Long.toString(12350));

         result = gtpc.getResultsFromAccumulo(settings.getAbsolutePath());
         resultArray = new JSONArray(result);

         assertTrue(resultArray.length() == 1);
         assertEquals(tweet3Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));

         //wide Time range and small bounding box to match tweet2 and tweet3
         gtpc.set_paramNorthCoordinate("49");
         gtpc.set_paramEastCoordinate("20");
         gtpc.set_paramSouthCoordinate("40");
         gtpc.set_paramWestCoordinate("0");
         gtpc.set_paramStartTime(Long.toString(12300));
         gtpc.set_paramEndTime(Long.toString(12399));

         result = gtpc.getResultsFromAccumulo(settings.getAbsolutePath());
         resultArray = new JSONArray(result);
         assertTrue(resultArray.length() == 2);
         assertEquals(tweet2Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));
         assertEquals(tweet3Json.getString("id_str"),resultArray.getJSONObject(1).getString("id_str"));

         //short time range and small bounding box to match only tweet 1
         gtpc.set_paramNorthCoordinate("42");
         gtpc.set_paramEastCoordinate("30");
         gtpc.set_paramSouthCoordinate("40");
         gtpc.set_paramWestCoordinate("28");
         gtpc.set_paramStartTime(Long.toString(12340));
         gtpc.set_paramEndTime(Long.toString(12346));

         result = gtpc.getResultsFromAccumulo(settings.getAbsolutePath());
         resultArray = new JSONArray(result);
         assertTrue(resultArray.length() == 1);
         assertEquals(tweet1Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));
     }

    @Test
    public void limitOffsetTest() throws Exception{
        GeoTimePeriodController gtpc = new GeoTimePeriodController();

        //wide Time range and wide bounding box to match all 3 tweets
        gtpc.set_paramNorthCoordinate("49");
        gtpc.set_paramEastCoordinate("30");
        gtpc.set_paramSouthCoordinate("40");
        gtpc.set_paramWestCoordinate("0");
        gtpc.set_paramStartTime(Long.toString(12300));
        gtpc.set_paramEndTime(Long.toString(12399));

        //limit to 2
        gtpc.set_paramLimit(2);
        gtpc.set_paramOffset(0);

        String result = gtpc.getResultsFromAccumulo(settings.getAbsolutePath());
        JSONArray resultArray = new JSONArray(result);
        assertTrue(resultArray.length() == 2);
        assertEquals(tweet1Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));
        assertEquals(tweet2Json.getString("id_str"),resultArray.getJSONObject(1).getString("id_str"));

        //limit to 1
        gtpc.set_paramLimit(1);
        gtpc.set_paramOffset(0);

        result = gtpc.getResultsFromAccumulo(settings.getAbsolutePath());
        resultArray = new JSONArray(result);
        assertTrue(resultArray.length() == 1);
        assertEquals(tweet1Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));

        //limit to 1 offset to 1
        gtpc.set_paramLimit(1);
        gtpc.set_paramOffset(1);

        result = gtpc.getResultsFromAccumulo(settings.getAbsolutePath());
        resultArray = new JSONArray(result);
        assertTrue(resultArray.length() == 1);
        assertEquals(tweet2Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));

        //limit to 2 offset to 1
        gtpc.set_paramLimit(2);
        gtpc.set_paramOffset(1);

        result = gtpc.getResultsFromAccumulo(settings.getAbsolutePath());
        resultArray = new JSONArray(result);
        assertTrue(resultArray.length() == 2);
        assertEquals(tweet2Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));
        assertEquals(tweet3Json.getString("id_str"),resultArray.getJSONObject(1).getString("id_str"));

        //limit to 2 offset to 10
        gtpc.set_paramLimit(2);
        gtpc.set_paramOffset(10);

        result = gtpc.getResultsFromAccumulo(settings.getAbsolutePath());
        resultArray = new JSONArray(result);
        assertTrue(resultArray.length() == 0);

        //limit to 2 offset to 2
        gtpc.set_paramLimit(2);
        gtpc.set_paramOffset(2);

        result = gtpc.getResultsFromAccumulo(settings.getAbsolutePath());
        resultArray = new JSONArray(result);
        assertTrue(resultArray.length() == 1);
        assertEquals(tweet3Json.getString("id_str"),resultArray.getJSONObject(0).getString("id_str"));

    }


}

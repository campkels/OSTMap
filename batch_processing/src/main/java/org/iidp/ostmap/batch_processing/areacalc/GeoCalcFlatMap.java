package org.iidp.ostmap.batch_processing.areacalc;


import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.util.Collector;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import java.io.Serializable;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Vector;


public class GeoCalcFlatMap implements FlatMapFunction<Tuple2<String,String>, Tuple3<String,Double,Integer>> {


    double equatorialEarthRadius = 6378.1370;
    double deg2rad = (Math.PI / 180.);
    int decimalPlaces = 10;

    public GeoCalcFlatMap(){
    }

    /**
     * Calculates the area defined by the coordinates of a users tweets
     * @param in user with extracted coordinates
     * @param out User with biggest area defined by tweets
     */
    @Override
    public void flatMap(Tuple2<String, String> in, Collector<Tuple3<String, Double,Integer>> out) {

        String userName = in.f0;
        String[] coords;
        Vector<double[]> coordinates = new Vector<>();

        String[] stringCoordSet;
        double area = 0.0;

        try {
            coords = in.f1.split("\\|");
            for (String entry: coords) {
                stringCoordSet = entry.split(",");
                double[] coordSet = {Double.parseDouble(stringCoordSet[0]),Double.parseDouble(stringCoordSet[1])};
                if(coordinates.size() < 4){
                    coordinates.add(coordSet);
                }else{
                    Vector<double[]> tempCoordinates0 = (Vector<double[]>) coordinates.clone();
                    Vector<double[]> tempCoordinates1 = (Vector<double[]>) coordinates.clone();
                    Vector<double[]> tempCoordinates2 = (Vector<double[]>) coordinates.clone();
                    Vector<double[]> tempCoordinates3 = (Vector<double[]>) coordinates.clone();
                    tempCoordinates0.remove(0);
                    tempCoordinates1.remove(1);
                    tempCoordinates2.remove(2);
                    tempCoordinates3.remove(3);
                    tempCoordinates0.add(coordSet);
                    tempCoordinates1.add(coordSet);
                    tempCoordinates2.add(coordSet);
                    tempCoordinates3.add(coordSet);
                    double originArea = getAreaInSquareKm(coordinates);
                    double Area0 = getAreaInSquareKm(tempCoordinates0);
                    double Area1 = getAreaInSquareKm(tempCoordinates1);
                    double Area2 = getAreaInSquareKm(tempCoordinates2);
                    double Area3 = getAreaInSquareKm(tempCoordinates3);
                    if(Area0 >= originArea && Area0 >= Area1 && Area0 >= Area2 && Area0 >= Area3){
                        coordinates = (Vector<double[]>) tempCoordinates0.clone();
                    }else if(Area1 >= originArea && Area1 >= Area0 && Area1 >= Area2 && Area1 >= Area3){
                        coordinates = (Vector<double[]>) tempCoordinates1.clone();
                    }else if(Area2 >= originArea && Area2 >= Area1 && Area2 >= Area0 && Area2 >= Area3){
                        coordinates = (Vector<double[]>) tempCoordinates2.clone();
                    }else if(Area3 >= originArea && Area3 >= Area1 && Area3 >= Area2 && Area3 >= Area0){
                        coordinates = (Vector<double[]>) tempCoordinates3.clone();
                    }
                }

            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        area = this.getAreaInSquareKm(coordinates);

        JSONObject data = new JSONObject();
        try {
            data.put("user",userName);
            data.put("area",area);
            JSONArray coordsJSON = new JSONArray();
            for(double[] entry: coordinates){
                JSONArray newCoords = new JSONArray();
                newCoords.put(entry[0]);
                newCoords.put(entry[1]);
                coordsJSON.put(newCoords);
            }
            data.put("coordinates",coordsJSON);
        } catch (JSONException e) {
            e.printStackTrace();
        }

        out.collect(new Tuple3<>(data.toString(),area,1));


    }

    /*public double getAreaInSquareMeters(){
        return 0.0;
    }*/

    /**
     * calculates the area the Vector of coordinates defines
     * @param coordinates all the coordinates from all the tweets o a single user
     * @return the biggest area defined by this coordinates
     */
    public double getAreaInSquareKm(Vector<double[]> coordinates){
        double[] a = coordinates.get(0);
        double[] b = coordinates.get(1);
        double[] c = coordinates.get(2);
        double ab = this.haversineInKm(a[0],a[1],b[0],b[1]);
        double ac = this.haversineInKm(a[0],a[1],c[0],c[1]);
        double bc = this.haversineInKm(b[0],b[1],c[0],c[1]);
        if(coordinates.size() == 3){
            return this.round(this.heronForm(ab,ac,bc),decimalPlaces);
        }else{
            double[] d = coordinates.get(3);
            double ad = this.haversineInKm(a[0],a[1],d[0],d[1]);
            double bd = this.haversineInKm(b[0],b[1],d[0],d[1]);
            double cd = this.haversineInKm(c[0],c[1],d[0],d[1]);
            double abc = this.heronForm(ab,ac,bc);
            double abd = this.heronForm(ab,ad,bd);
            double acd = this.heronForm(ac,ad,cd);
            double bcd = this.heronForm(bc,bd,cd);
            boolean konvex = false;
            if(abc > abd && abc > acd && abc > bcd){
                if(abc*0.9 <= abd + acd + bcd && abd + acd + bcd <= abc*1.1){
                    return this.round(abc,decimalPlaces);
                }else{
                    konvex = true;
                }
            }else if(abd > abc && abd > acd && abd > bcd){
                if(abd*0.9 <= abc + acd + bcd && abd*1.1 >= abc + acd + bcd){
                    return this.round(abd,decimalPlaces);
                }else{
                    konvex = true;
                }
            }else if(acd > abc && acd > abd && acd > bcd){
                if(acd*0.9 <= abd + abc + bcd && acd*1.1 >= abd + abc + bcd){
                    return this.round(acd,decimalPlaces);
                }else{
                    konvex = true;
                }
            }else if(bcd > abc && bcd > abd && bcd > acd){
                if(bcd*0.9 <= abd + acd + abc && bcd*1.1 >= abd + acd + abc){
                    return this.round(bcd,decimalPlaces);
                }else{
                    konvex = true;
                }
            }else {
                konvex = true;
            }
            if(konvex){
                if(ab<ac){
                    if(ac<ad){
                        // ad is the diagonal
                        return this.round(this.heronForm(ab,bd,ad) + this.heronForm(ac,cd,ad),decimalPlaces);
                    }else{
                        // ac is the diagonal
                        return this.round(this.heronForm(ab,bc,ac) + this.heronForm(ad,cd,ac),decimalPlaces);
                    }
                }else{
                    if(ab<ad){
                        // ad is the diagonal
                        return this.round(this.heronForm(ab,bd,ad) + this.heronForm(ac,cd,ad),decimalPlaces);
                    }else{
                        // ab is the diagonal
                        return this.round(this.heronForm(ac,bc,ab) + this.heronForm(ad,bd,ab),decimalPlaces);
                    }
                }
            }
        }
        return 0.333;
    }

    /**
     * Calculates the area of a triangle using the heron formula
     * @param sideA first side of the triangle
     * @param sideB second side of the triangle
     * @param sideC third side of the triangle
     * @return the area defined by the triangle
     */
    public double heronForm(double sideA, double sideB, double sideC){
        double s = (sideA+sideB+sideC)/2;
        return Math.sqrt(s*(s-sideA)*(s-sideB)*(s-sideC));
    }


    /**
     * calculates the distance between two sets of coordinates using the haversine formula
     * @param lat1 latitude coordinate 1
     * @param long1 longitude coordinate 1
     * @param lat2 latitude coordinate 2
     * @param long2 longitude coordinate 2
     * @return the distance betweend the two coordinates
     */
    public double haversineInKm(double long1, double lat1, double long2, double lat2){
        double dlong = (long2 - long1) * deg2rad;
        double dlat = (lat2 - lat1) * deg2rad;
        double a = Math.pow(Math.sin(dlat / 2.), 2.) + Math.cos(lat1 * deg2rad) * Math.cos(lat2 * deg2rad) * Math.pow(Math.sin(dlong / 2.), 2.);
        double c = 2D * Math.atan2(Math.sqrt(a), Math.sqrt(1. - a));
        double d = equatorialEarthRadius * c;

        return d;
    }

    /**
     * Rounds the given double to the given decimal places
     * @param value Number to round
     * @param places number of decimal places to round to
     * @return The rounded number
     */
    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        BigDecimal bd = null;
        try{
            bd = new BigDecimal(value);
            bd = bd.setScale(places, RoundingMode.HALF_UP);
        }catch(Exception e){
            e.printStackTrace();
            return 0.;
        }
        return bd.doubleValue();
    }
}

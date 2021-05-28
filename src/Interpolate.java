import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;

import javax.vecmath.Point3d;
import java.io.*;
import java.util.ArrayList;

import java.util.concurrent.TimeUnit;



public class Interpolate {

    public static void main(String[] args) {
        long start_time = System.currentTimeMillis();

        if (args.length != 7){
            System.out.println("ERROR: Args should be: <framerate> <time range start> <time range end> <tension [0-1]> <description file path(with '/')> <input frames directory(with '/')> <output directory (with '/')>");
            System.exit(1);
        }
        int framerate = Integer.parseInt(args[0]);
        int time_start = Integer.parseInt(args[1]);
        int time_end = Integer.parseInt(args[2]);
        float tension = Float.parseFloat(args[3]);
        String descriptionFilePath = args[4];
        String inputFramesDirectory = args[5];
        String outputDirectory = args[6];


        DescriptionFile descriptionFile = new DescriptionFile(descriptionFilePath);

        if (time_start < (int) descriptionFile.getKeyframes().get(0).y){
            System.out.println("ERROR: The provided start of the time range is lower than the first keyframe provided in description file.");
            System.exit(1);
        }

        if (time_end > (int) descriptionFile.getKeyframes().get(descriptionFile.getKeyframes().size() - 1).y){
            System.out.println("ERROR: The provided end of the time range is higher than the last keyframe provided in description file.");
            System.exit(1);
        }

        ArrayList<Tuple> selectedKeyFrames = getSelectedKeyframes(descriptionFile, time_start, time_end);

        //Read files in the description file:
        ArrayList<Tuple<ObjFile, Integer>> files = new ArrayList<>();
        for (int i = 0; i < descriptionFile.getKeyframes().size(); i++){
            files.add(new Tuple<>(new ObjFile(inputFramesDirectory + descriptionFile.getKeyframes().get(i).x), (int)descriptionFile.getKeyframes().get(i).y));
        }

        ArrayList<ObjFile> outputs = new ArrayList<>();


        for (int i = 0; i < descriptionFile.getKeyframes().size() - 1; i++){
            int nOfOutFiles = Math.abs(((int) descriptionFile.getKeyframes().get(i+1).y - (int) descriptionFile.getKeyframes().get(i).y)); /// framerate); // 50
            ObjFile obj0;
            ObjFile obj1;
            ObjFile obj2;
            ObjFile obj3;
            if (i == 0){
                obj0 = files.get(0).x;
                obj1 = files.get(0).x;
                obj2 = files.get(1).x;
                obj3 = files.get(2).x;
            } else if (i == descriptionFile.getKeyframes().size() - 2){  // the [-2] element
                obj0 = files.get(i-1).x;
                obj1 = files.get(i).x;
                obj2 = files.get(i+1).x;
                obj3 = files.get(i+1).x;
            } else {
                obj0 = files.get(i-1).x;
                obj1 = files.get(i).x;
                obj2 = files.get(i + 1).x;
                obj3 = files.get(i + 2).x;
            }
            for(int j = 0; j < nOfOutFiles; j++){
                double current_u = (double) j / nOfOutFiles;
                int currentFrame = files.get(i).y + j;
                if (currentFrame < time_start){
                    continue;
                }
                if ((currentFrame - time_start) % framerate != 0){
                    continue;
                }

                if (currentFrame > time_end){
                    break;
                }
                ObjFile out = new ObjFile(String.format("%soutput_%04d.obj", outputDirectory, j), files.get(i).x.getRestOfFile(), currentFrame);
                for (int k = 0; k < files.get(0).x.getNOfVertices(); k++){
                    Point3d res = calculateCatmullRom(obj0.getVertexAtIndex(k), obj1.getVertexAtIndex(k), obj2.getVertexAtIndex(k), obj3.getVertexAtIndex(k), current_u, tension);
                    out.addVertex(res);
                }
                outputs.add(out);
            }
        }

        if ((time_end - time_start) % framerate == 0) {
            for (int i = 0; i < files.size(); i++) {
                if (time_end == files.get(i).y) {
                    ObjFile temp = files.get(i).x;
                    temp.setKeyframeN(time_end);
                    outputs.add(temp);
                    break;
                }
            }
        }

        for (int i = 0; i < outputs.size(); i++){
            //System.out.printf("keyframe %d: %d%n", i, outputs.get(i).getKeyframeN());
            outputs.get(i).saveToFile(String.format("%soutput_%04d.obj", outputDirectory, i));
        }

        long end_time = System.currentTimeMillis();

        float time_taken = (float) ((end_time - start_time) / 1000);

        System.out.println("Done ! \n Generated " + outputs.size() + " frames. Files are saved in the directory '" +outputDirectory + "'.");
        System.out.printf("Time taken: %.2fs%n", time_taken);
    }

    private static ArrayList<Tuple> getSelectedKeyframes(DescriptionFile descriptionFile, int time_start, int time_end){
        int startIndex = 0;
        ArrayList<Tuple> keyframes = descriptionFile.getKeyframes();
        ArrayList<Tuple> selectedKeyframes = new ArrayList<>();
        while(true){
            int curr = (int) keyframes.get(startIndex).y;
            if (curr == time_start){
                break;
            }
            if (curr > time_start) {
                startIndex -= 1;
                break;
            }
            startIndex += 1;
        }

        int endIndex = startIndex;
        while(true){
            int curr = (int) keyframes.get(endIndex).y;
            if (curr >= time_end){
                break;
            }
            endIndex += 1;
        }

        for (int i = startIndex; i <= endIndex; i++){
            selectedKeyframes.add(keyframes.get(i));
        }
        return selectedKeyframes;

    }

    private static ObjFile readObjFile(String path){
        return new ObjFile(path);
    }

    private static DescriptionFile readDescriptionFile(String path) { return new DescriptionFile(path); }

    private static Point3d calculateCatmullRom(Point3d p0, Point3d p1, Point3d p2, Point3d p3, double u, float tension){
        double[][] points = {
                {p0.x, p1.x, p2.x, p3.x},
                {p0.y, p1.y, p2.y, p3.y},
                {p0.z, p1.z, p2.z, p3.z}};

        RealMatrix pointsMat = MatrixUtils.createRealMatrix(points);

        double[][] tensions =
                {{-tension, 2 * tension, -tension, 0},
                {2 - tension, tension - 3, 0, 1},
                {tension - 2, 3 - 2 * tension, tension, 0},
                {tension, -tension, 0, 0}};

        RealMatrix tensionsMat = MatrixUtils.createRealMatrix(tensions);


        double[][] us = {{Math.pow(u, 3)}, {Math.pow(u, 2)}, {u}, {1}};
        RealMatrix usMatrix = MatrixUtils.createRealMatrix(us);

        RealMatrix result = pointsMat.multiply(tensionsMat).multiply(usMatrix);

        return new Point3d(result.getEntry(0, 0), result.getEntry(1,0), result.getEntry(2,0));
    }





}

class DescriptionFile{
    ArrayList<Tuple> keyframes;

    DescriptionFile(String path){
        this.keyframes = new ArrayList<>();
        readDescriptionFile(path);
    }

    private void readDescriptionFile(String path) {
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(path));
            String line = reader.readLine();

            while (line != null) {
                String keyframePath = line.split(" ")[0];
                Integer keyframeFrame = Integer.parseInt(line.split(" ")[1]);
                Tuple<String, Integer> tuple = new Tuple<String, Integer>(keyframePath, keyframeFrame);
                this.keyframes.add(tuple);
                // read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public ArrayList<Tuple> getKeyframes() {
        return this.keyframes;
    }

    public String toString(){
        StringBuilder out = new StringBuilder();
        for (Tuple keyframe: this.keyframes){
            out.append(String.format("Path: %s, Keyframe: %d%n", keyframe.x, keyframe.y));
        }
        return out.toString();
    }
}

class ObjFile {
    private ArrayList<Point3d> vertices;
    private String[] restOfFile;
    private String path;
    private int keyframeN;

    private int nOfVertices;

    ObjFile(String path){
        this.restOfFile = new String[3];
        this.restOfFile[0] = "";
        this.restOfFile[1] = "";
        this.restOfFile[2] = "";
        this.path = path;
        this.vertices = new ArrayList<>();
        readObjFile();
        this.nOfVertices = this.vertices.size();
    }

    ObjFile(String path, String[] restOfFile, int keyframeN){
        this.restOfFile = restOfFile;
        this.vertices = new ArrayList<>();
        this.keyframeN = keyframeN;
    }

    public void saveToFile(String pathOut){
        File fout = new File(pathOut);
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(fout);
            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));

            bw.write("# This file uses centimeters as units for non-parametric coordinates.");
            bw.newLine();
            bw.newLine();


            for (int i = 0; i < this.nOfVertices / 3; i++){
                bw.write(preparePointForPrint(this.vertices.get(i)));
                bw.newLine();
            }
            bw.write(this.restOfFile[0]);

            for (int i = this.nOfVertices / 3; i < 2 * (this.nOfVertices / 3); i++){
                bw.write(preparePointForPrint(this.vertices.get(i)));
                bw.newLine();
            }
            bw.write(this.restOfFile[1]);

            for (int i = 2 * (this.nOfVertices / 3); i < this.nOfVertices; i++){
                bw.write(preparePointForPrint(this.vertices.get(i)));
                bw.newLine();
            }
            bw.write(this.restOfFile[2]);


            bw.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }


    }

    public void setKeyframeN(int keyframeN) {
        this.keyframeN = keyframeN;
    }

    private String preparePointForPrint(Point3d point){
        return String.format("v %.6f %.6f %.6f", point.getX(), point.getY(), point.getZ());
    }

    public void addVertex(Point3d vertex){
        this.vertices.add(vertex);
        this.nOfVertices += 1;
    }

    public ArrayList<Point3d> getVertices() {
        return vertices;
    }

    public int getKeyframeN() {
        return keyframeN;
    }

    public String[] getRestOfFile() {
        return this.restOfFile;
    }

    public Point3d getVertexAtIndex(int index){
        return this.vertices.get(index);
    }

    public int getNOfVertices() { return this.nOfVertices; }

    public String toString(){
        StringBuilder out = new StringBuilder();
        for (Point3d vertex : this.vertices) {
            out.append(String.format("x: %f, y: %f, z: %f%n", vertex.getX(), vertex.getY(), vertex.getZ()));
        }
        out.append("\n Rest of file:\n");
        out.append(this.restOfFile);
        return out.toString();
    }

    private void readObjFile() {
        int restOfFileIndex = -1;
        String prev = "";
        BufferedReader reader;
        try {
            reader = new BufferedReader(new FileReader(
                    this.path));
            String line = reader.readLine();
            line = reader.readLine();
            line = reader.readLine();

            while (line != null) {
                if (line.split(" ")[0].equals("vt") && prev.equals("v")) {
                    restOfFileIndex += 1;
                    prev = "";
                }
                if (line.split(" ")[0].equals("v")) {
                    String[] splitted = line.split(" ");
                    Point3d v = new Point3d(Float.parseFloat(splitted[1]), Float.parseFloat(splitted[2]), Float.parseFloat(splitted[3]));
                    this.vertices.add(v);
                    prev = splitted[0];
                } else {
                    this.restOfFile[restOfFileIndex] += line + "\n";
                }

                // read next line
                line = reader.readLine();
            }
            reader.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

class Tuple<X, Y> {
    public final X x;
    public final Y y;
    public Tuple(X x, Y y) {
        this.x = x;
        this.y = y;
    }
}
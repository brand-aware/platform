import java.io.*;
import java.nio.file.*;
public class FivePatientDiagnostic {
 public static void main(String[] args) throws Exception {
  DataHandler cy3 = new DataHandler(5,25), cy5 = new DataHandler(5,25);
  cy3.readCSVSheet("../../data/OliverNewPatients3_24Cy3_condensed.csv");
  cy5.readCSVSheet("../../data/OliverNewPatients3_24Cy5_condensed.csv");
  cy3.readTXTData("../../data/idlist_condensed.txt");
  cy5.readTXTData("../../data/idlist_condensed.txt");
  Normalization normalization = new Normalization();
  double[][][] normalized = normalization.predict(cy3,cy5,false,"Marker; Cy3/Cy5 pre-labeled Albumin","bkg control; Albumin, unlabeled");
  BioMarkerSelection_Local selection = new BioMarkerSelection_Local() {
   public File createNewArff(File source, long index) throws IOException {
    if (index == 4) Files.copy(source.toPath(), Paths.get("captured-data_temp4.csv"), StandardCopyOption.REPLACE_EXISTING);
    try { return super.createNewArff(source,index); }
    catch (IOException e) {
     Path preserved = Paths.get("failed-" + source.getName());
     Files.copy(source.toPath(),preserved,StandardCopyOption.REPLACE_EXISTING);
     System.err.println("PRESERVED " + preserved.toAbsolutePath());
     throw e;
    }
   }
  };
  selection.setID(normalization.getIDList());
  selection.backwardsElimination(normalized);
  System.out.println("FIVE PATIENT RUN COMPLETE");
 }
}

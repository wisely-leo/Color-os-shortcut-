import com.android.apksig.ApkVerifier;
import java.io.File;
public class VerifyApk {
  public static void main(String[] a) throws Exception {
    ApkVerifier.Result r = new ApkVerifier.Builder(new File(a[0])).build().verify();
    System.out.println("verified(v1+)= " + r.isVerified());
    System.out.println("v1= " + r.isVerifiedUsingV1Scheme());
    System.out.println("v2= " + r.isVerifiedUsingV2Scheme());
    System.out.println("v3= " + r.isVerifiedUsingV3Scheme());
    if(!r.getErrors().isEmpty()) System.out.println("errors= " + r.getErrors());
    if(!r.getWarnings().isEmpty()) System.out.println("warnings= " + r.getWarnings());
  }
}

import com.android.apksig.ApkSigner;
import java.io.File;
import java.io.FileInputStream;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

public class SignApk {
    public static void main(String[] a) throws Exception {
        String in=a[0], out=a[1], ks=a[2], ksPass=a[3], alias=a[4];
        KeyStore store;
        try {
            store = KeyStore.getInstance("PKCS12");
            store.load(new FileInputStream(ks), ksPass.toCharArray());
        } catch (Exception e) {
            store = KeyStore.getInstance("JKS");
            store.load(new FileInputStream(ks), ksPass.toCharArray());
        }
        PrivateKey key = (PrivateKey) store.getKey(alias, ksPass.toCharArray());
        X509Certificate cert = (X509Certificate) store.getCertificate(alias);
        List<X509Certificate> certs = new ArrayList<X509Certificate>();
        certs.add(cert);
        ApkSigner.SignerConfig signer = new ApkSigner.SignerConfig.Builder(alias, key, certs).build();
        List<ApkSigner.SignerConfig> signers = new ArrayList<ApkSigner.SignerConfig>();
        signers.add(signer);
        new ApkSigner.Builder(signers)
            .setInputApk(new File(in))
            .setOutputApk(new File(out))
            .setMinSdkVersion(26)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .setAlignmentPreserved(true)
            .setAlignFileSize(false)
            .build().sign();
        System.out.println("SIGNED_OK");
    }
}

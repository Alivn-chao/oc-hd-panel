import dev.bec.hdpanel.AppModel;
import dev.bec.hdpanel.client.AppPainter;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/** Usage: GenericPreview output-directory captured-layout-and-values.json */
public class GenericPreview {
    public static void main(String[] args) throws Exception {
        File out=new File(args[0]);out.mkdirs();
        ImageIO.write(AppPainter.paint(AppModel.WELCOME,"{}",false,0),"png",new File(out,"preview-welcome.png"));
        JsonObject sample=new JsonParser().parse(new String(Files.readAllBytes(Paths.get(args[1])),StandardCharsets.UTF_8)).getAsJsonObject();
        String layout=sample.get("layout").toString(),values=sample.get("values").toString();
        ImageIO.write(AppPainter.paint(layout,values,true,0),"png",new File(out,"preview-generic.png"));
        ImageIO.write(AppPainter.paint(layout,values,true,1),"png",new File(out,"preview-generic-help.png"));
        System.out.println("Actual AppPainter previews written (simulated telemetry).");
    }
}

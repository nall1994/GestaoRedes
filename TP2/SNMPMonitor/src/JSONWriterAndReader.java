import org.json.*;
import org.apache.commons.io.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class JSONWriterAndReader {
    private static String path_to_database = "../Database/database.json";

    public static synchronized void writeToDatabase(List<InterfaceValues> ifValues,Agente agente) {
        //DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/mm/yyyy");
        String todaysDate = LocalDate.now().toString();
        JSONObject novaConsulta = new JSONObject();
        novaConsulta.put("dataConsulta",todaysDate);
        JSONArray interfacesNovaConsulta = new JSONArray();
        JSONObject singularInterface;
        for(InterfaceValues iv : ifValues) {
            singularInterface = new JSONObject();
            singularInterface.put("ifIndex",iv.getIfIndex());
            singularInterface.put("ifDescr",iv.getIfDescr());
            singularInterface.put("ifPhysAddress",iv.getIfPhysAddress());
            singularInterface.put("inOctets",iv.getInOctets());
            singularInterface.put("outOctets",iv.getOutOctets());
            singularInterface.put("difOctets",iv.getDifOctets());
            interfacesNovaConsulta.put(singularInterface);
        }
        //System.out.println(interfacesNovaConsulta.toString(4));
        novaConsulta.put("valoresInterfaces",interfacesNovaConsulta);
        File file = new File(path_to_database);
        try {
            String content = FileUtils.readFileToString(file,"utf-8");
            JSONArray infoAgentes = new JSONArray(content);
            JSONArray newInfoAgentes = new JSONArray();
            for(int i = 0; i < infoAgentes.length();i++) {
                JSONObject obj = infoAgentes.getJSONObject(i);
                if((obj.getString("ipAgente").equalsIgnoreCase(agente.getIp())) && (obj.getString("portaAgente").equalsIgnoreCase(agente.getPorta())))
                    obj.getJSONArray("consultas").put(novaConsulta);
                newInfoAgentes.put(obj);
            }
            FileUtils.writeStringToFile(file,newInfoAgentes.toString(4),"utf-8",false);
            System.out.println("Finished Writing To DataBase!");
        } catch(IOException ioe) {
            System.out.println("Não foi possível ler do ficheiro!");
        }

    }
}

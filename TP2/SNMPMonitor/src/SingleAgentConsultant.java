import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.*;
import java.util.ArrayList;

public class SingleAgentConsultant extends Thread {
    private Agente agente;
    private String path_to_database;
    private Snmp snmp;
    private JSONWriterAndReader database_handler;

    public SingleAgentConsultant(Agente agente, String path_to_database) {
        this.agente = agente; this.path_to_database = path_to_database;
    }

    public void run() {
        //Ir buscar ifDescr e ifPhysAddress, das interfaces e inicializar todos os ficheiros de interface dele,
        //nao esquecer do array de consultas vazio também
        try{
            TransportMapping transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();
            ArrayList<String> pollingTimes = extractPollingTimes();

            for(int i = 1; i <= agente.getNumber_interfaces();i++) {
                String ifDescr = getAsString(new OID("1.3.6.1.2.1.2.2.1.2." + i));
                String ifPhysAddress = getAsString(new OID("1.3.6.1.2.1.2.2.1.6." + i));
                String ifIndex = String.valueOf(i);
                //get polling time
                String polling_time = pollingTimes.get(i-1);
                JSONObject obj = new JSONObject();
                obj.put("ifIndex","" + i);
                obj.put("ifDescr",ifDescr);
                obj.put("ifPhysAddress",ifPhysAddress);
                obj.put("pollingTime",polling_time);
                obj.put("consultas",new JSONArray());
                File file = new File(path_to_database + agente.getIp() + "_" + agente.getPorta() + "/interface" +i + ".json");
                FileUtils.writeStringToFile(file,obj.toString(4),"utf-8",false);
                //write these 4 parameters to interfaces's JSON file and consultas = []
                //start the thread afterwards
                if(!polling_time.equals("dynamic"));
                InterfaceThread ithread = new InterfaceThread(i,polling_time);
                ithread.start();
            }
        } catch(Exception ex) {
            System.out.println("couldn't connect snmp session!");
        }
    }

    private ArrayList<String> extractPollingTimes() {
        File file = new File( "Database/config/" + agente.getIp() + "_" + agente.getPorta() + "_interfaces.config");
        ArrayList<String> pollingTimes = new ArrayList<>();
        try{
            BufferedReader reader = new BufferedReader(new FileReader(file));
            String line = "";
            while((line = reader.readLine()) != null) {
                String[] parts = line.split(":");
                if(parts[1].equals("dynamic"))
                    pollingTimes.add("dynamic");
                else pollingTimes.add(parts[2]);
            }
        } catch(FileNotFoundException fnfe) {
            System.out.println("The configuration file was not found!");
        } catch(IOException ioe) {
            System.out.println("Could not read from configuration file!");
        }

        return pollingTimes;
    }

    private String getAsString(OID oid) throws IOException {
        ResponseEvent event = get(new OID[] {oid});
        if(event != null) {
            return event.getResponse().get(0).getVariable().toString();
        } else {
            return "";
        }

    }

    private ResponseEvent get(OID oids[]) throws IOException {
        PDU pdu = new PDU();
        for(OID oid : oids) {
            pdu.add(new VariableBinding(oid));
        }
        pdu.setType(PDU.GET);
        ResponseEvent event = snmp.send(pdu,getTarget(),null);
        return event;
    }

    private Target getTarget() {
        Address targetAddress = GenericAddress.parse("udp:" + agente.getIp() + "/" + agente.getPorta());
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString("public"));
        target.setAddress(targetAddress);
        target.setRetries(2);
        target.setTimeout(1500);
        target.setVersion(SnmpConstants.version2c);
        return target;
    }
}

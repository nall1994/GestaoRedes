import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;

public class InterfaceThread extends Thread {
    private int ifIndex;
    private String type_of_poll;
    private int polling_time;
    Snmp snmp;
    Agente agente;
    private long last_dif_value;

    public InterfaceThread(int ifIndex,String type_of_poll,Agente agente) {
        if(!type_of_poll.equals("dynamic")){
            this.polling_time = Integer.parseInt(type_of_poll);
            this.type_of_poll = "fixed";
        }
        else {
            this.polling_time = 3000;
            this.type_of_poll = "dynamic";
        }
        this.ifIndex = ifIndex;
        this.last_dif_value = -1;
        this.agente = agente;
    }

    public void run() {
        try{
            TransportMapping transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();

            while(true) {
                //Numa dada execução, os polling times dinâmicos começam sempre em 3000, ver se deve ser assim ou não!
                String inOctets = getAsString(new OID("1.3.6.1.2.1.2.2.1.10." + this.ifIndex));
                String outOctets = getAsString(new OID("1.3.6.1.2.1.2.2.1.16." + this.ifIndex));
                long difOctets = Math.abs(Long.parseLong(outOctets) - Long.parseLong(inOctets));
                File file = new File("../Database/" + agente.getIp() + "_" + agente.getPorta() + "/interface" + this.ifIndex + ".json");
                if(existsDifference(difOctets) && this.last_dif_value != -1) {
                    if((this.type_of_poll.equals("dynamic")) && (this.polling_time > 1000)) this.polling_time -= 500;
                    String content = FileUtils.readFileToString(file,"utf-8");
                    JSONObject agent_interface = new JSONObject(content);
                    JSONArray consultas = agent_interface.getJSONArray("consultas");
                    LocalDateTime data_atual = LocalDateTime.now(); // formatar a data para ler depois na web
                    JSONObject single_consult = new JSONObject();
                    agent_interface.put("pollingTime",String.valueOf(this.polling_time));
                    single_consult.put("dataConsulta",data_atual.toString());
                    single_consult.put("inOctets",inOctets);
                    single_consult.put("outOctets",outOctets);
                    single_consult.put("difOctets",String.valueOf(difOctets));
                    consultas.put(single_consult);
                    //System.out.println(consultas.toString(4));
                    agent_interface.put("consultas",consultas);
                    this.last_dif_value = difOctets;
                    FileUtils.writeStringToFile(file,agent_interface.toString(4),"utf-8",false);
                } else if(this.last_dif_value == -1) {
                    String content = FileUtils.readFileToString(file,"utf-8");
                    JSONObject agent_interface = new JSONObject(content);
                    JSONArray consultas = agent_interface.getJSONArray("consultas");
                    LocalDateTime data_atual = LocalDateTime.now(); // formatar a data para ler depois na web
                    JSONObject single_consult = new JSONObject();
                    agent_interface.put("pollingTime",String.valueOf(this.polling_time));
                    single_consult.put("dataConsulta",data_atual.toString());
                    single_consult.put("inOctets",inOctets);
                    single_consult.put("outOctets",outOctets);
                    single_consult.put("difOctets",String.valueOf(difOctets));
                    consultas.put(single_consult);
                    //System.out.println(consultas.toString(4));
                    agent_interface.put("consultas",consultas);
                    this.last_dif_value = difOctets;
                    FileUtils.writeStringToFile(file,agent_interface.toString(4),"utf-8",false);
                } else {
                    if((this.type_of_poll.equals("dynamic")) && (this.polling_time < 10000)) this.polling_time += 500;
                    String content = FileUtils.readFileToString(file,"utf-8");
                    JSONObject agent_interface = new JSONObject(content);
                    JSONArray consultas = agent_interface.getJSONArray("consultas");
                    LocalDateTime data_atual = LocalDateTime.now(); // formatar a data para ler depois na web
                    JSONObject single_consult = new JSONObject();
                    agent_interface.put("pollingTime",String.valueOf(this.polling_time));
                    single_consult.put("dataConsulta",data_atual.toString());
                    single_consult.put("inOctets",inOctets);
                    single_consult.put("outOctets",outOctets);
                    single_consult.put("difOctets",String.valueOf(difOctets));
                    consultas.put(single_consult);
                    this.last_dif_value = difOctets;
                    agent_interface.put("consultas",consultas);
                    FileUtils.writeStringToFile(file,agent_interface.toString(4),"utf-8",false);
                }
                try{
                    Thread.sleep(polling_time);
                }catch(InterruptedException ie) {
                    System.out.printf("Something went wrong! The polling stopped");
                    break;
                }
            }
        } catch(java.net.SocketException se) {
            System.out.println("Could not connect with SNMP!");
        } catch(IOException ioe) {
            System.out.println("Not able to listen to SNMP connections!");
        }
        //while(true) do poll based on polling_time. If it is -1 that means it is a dynamic poll, or else it is time fixed.
    }

    private boolean existsDifference(long dif_octets) {
        if(last_dif_value == dif_octets) return false;
        else return true;
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

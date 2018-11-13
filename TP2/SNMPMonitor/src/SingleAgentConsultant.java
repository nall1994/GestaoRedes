import java.io.IOException;
import java.lang.Thread;

import org.json.JSONWriter;
import org.snmp4j.*;
import org.snmp4j.transport.*;
import org.snmp4j.smi.*;
import org.snmp4j.event.*;
import org.snmp4j.mp.*;
import java.util.List;
import java.util.ArrayList;


public class SingleAgentConsultant extends Thread{
    private Agente agente;
    private int number_interfaces; // oid = 1.3.6.1.2.1.2.1.0
    private Snmp snmp;
    private JSONWriterAndReader database_handler;

    public SingleAgentConsultant(Agente agente) {
        this.agente = agente;
    }

    public void run() {
        List<InterfaceValues> ifValues = new ArrayList<>();
        try {
            //ifDescr_oid: 1.3.6.1.2.1.2.2.1.2 + nr interface
            //ifPhysAddress_oid: 1.3.6.1.2.1.2.2.1.6 + nr interface
            //inOctets_oid: 1.3.6.1.2.1.2.2.1.10 + nr interface
            //outOctets_oid: 1.3.6.1.2.1.2.2.1.16 + nr interface
            TransportMapping transport = new DefaultUdpTransportMapping();
            this.snmp = new Snmp(transport);
            transport.listen();
            this.number_interfaces = Integer.parseInt(getAsString(new OID("1.3.6.1.2.1.2.1.0")));
            InterfaceValues singIfValues;
            for(int i = 1; i<=this.number_interfaces;i++) {
                //getAll Variables and put it in to json
                String ifIndex = String.valueOf(i);
                String ifDescr =  getIfDescrAsString(new OID("1.3.6.1.2.1.2.2.1.2." + i));
                String ifPhysAddress = getAsString(new OID("1.3.6.1.2.1.2.2.1.6." + i));
                String inOctets = getAsString(new OID("1.3.6.1.2.1.2.2.1.10." + i));
                String outOctets = getAsString(new OID("1.3.6.1.2.1.2.2.1.16." + i));
                singIfValues = new InterfaceValues(ifIndex,ifDescr,ifPhysAddress,inOctets,outOctets);
                ifValues.add(singIfValues);
            }

            database_handler.writeToDatabase(ifValues,agente);
        } catch(IOException ioe) {
            System.out.println("Erro!");
        }

        //Agora é retirar as infos todas!



    }

    private String getAsString(OID oid) throws IOException {
        ResponseEvent event = get(new OID[] {oid});
        if(event != null) {
            return event.getResponse().get(0).getVariable().toString();
        } else {
            return "";
        }

    }

    private String getIfDescrAsString(OID oid) throws IOException {
        ResponseEvent event = get(new OID[] {oid});
        if(event != null) {
            /*Ainda não sei como converter esta para uma string que dê para ler*/
            String s = event.getResponse().get(0).getVariable().toString();
            OctetString os = new OctetString(s);
            return os.toHexString();
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

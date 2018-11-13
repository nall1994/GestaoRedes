import java.io.IOException;
import java.lang.Thread;
import org.snmp4j.*;
import org.snmp4j.transport.*;
import org.snmp4j.smi.*;
import org.snmp4j.event.*;
import org.snmp4j.mp.*;


public class SingleAgentConsultant extends Thread{
    private Agente agente;
    private int number_interfaces; // oid = 1.3.6.1.2.1.2.1.0
    private Snmp snmp;

    public SingleAgentConsultant(Agente agente) {
        this.agente = agente;
    }

    public void run() {
        try {
            TransportMapping transport = new DefaultUdpTransportMapping();
            this.snmp = new Snmp(transport);
            transport.listen();
            this.number_interfaces = Integer.parseInt(getAsString(new OID("1.3.6.1.2.1.2.1.0")));
            //System.out.println(this.number_interfaces);
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

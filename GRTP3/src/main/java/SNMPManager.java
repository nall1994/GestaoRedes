import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.Target;
import org.snmp4j.TransportMapping;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.MPv3;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.IOException;

public class SNMPManager {
    private Snmp snmp;
    private String address;

    public SNMPManager(String add) {
        super();
        this.address = add;
        try{
            start();
        }catch(IOException e){
            throw new RuntimeException(e);
        }
    }

    public void start() throws IOException {
        TransportMapping transport = new DefaultUdpTransportMapping();
        snmp = new Snmp(transport);
        //Nunca esquecer!
        transport.listen();
    }

    public String getAsString(OID oid) throws IOException {
        ResponseEvent event = get(new OID[] { oid });
        PDU resposta = event.getResponse();
        return event.getResponse().get(0).getVariable().toString();
    }

    public void setValueString(OID oid,String value) throws IOException{
        ResponseEvent event = setString(oid, value);
        PDU resposta = event.getResponse();
    }

    public void setValueInt(OID oid,int value) throws IOException{
        ResponseEvent event = setInt(oid, value);
        PDU resposta = event.getResponse();
    }

    public ResponseEvent setString(OID oid,String value) throws IOException{
        PDU pdu = new PDU();
        VariableBinding varBind = new VariableBinding(oid, new OctetString(value));
        pdu.add(varBind);
        pdu.setType(PDU.SET);

        return snmp.set(pdu,getTarget());
    }

    public ResponseEvent setInt(OID oid,int value) throws IOException{
        PDU pdu = new PDU();
        VariableBinding varBind = new VariableBinding(oid, new Integer32(value));
        pdu.add(varBind);
        pdu.setType(PDU.SET);

        return snmp.set(pdu,getTarget());
    }

    public ResponseEvent get(OID oids[]) throws IOException {
        PDU pdu = new PDU();
        for (OID oid : oids) {
            pdu.add(new VariableBinding(oid));
        }
        pdu.setType(PDU.GET);
        ResponseEvent event = snmp.send(pdu, getTarget(), null);
        if(event != null) {
            return event;
        }
        throw new RuntimeException("GET timed out");
    }

    private Target getTarget() {
        Address targetAddress = GenericAddress.parse(address);
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString("public"));
        target.setAddress(targetAddress);
        target.setRetries(2);
        target.setTimeout(1500);
        target.setVersion(SnmpConstants.version2c);
        return target;
    }

}

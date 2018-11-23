import java.io.IOException;
import java.lang.Thread;

import org.json.JSONWriter;
import org.snmp4j.*;
import org.snmp4j.transport.*;
import org.snmp4j.smi.*;
import org.snmp4j.event.*;
import org.snmp4j.mp.*;

import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;


public class SingleAgentConsultant extends Thread{
    private Agente agente;
    private int number_interfaces; // oid = 1.3.6.1.2.1.2.1.0
    private Snmp snmp;
    private JSONWriterAndReader database_handler;
    private HashMap<Integer,Long> difOctets_map;

    public SingleAgentConsultant(Agente agente) {
        this.agente = agente;
        this.difOctets_map = new HashMap<Integer,Long>();
    }

    public void run() {
            Interfaces interfaces = new Interfaces();
            List<InterfaceValues> ifValues = new ArrayList<>();
            try {
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
                    singIfValues = new InterfaceValues(ifIndex,ifDescr,ifPhysAddress,inOctets,outOctets,3000);
                    Long dif = Long.parseLong(singIfValues.getDifOctets());
                    difOctets_map.put(i,dif);
                    interfaces.addInterfaceInfo(i,singIfValues);
                    ifValues.add(singIfValues);
                }

                database_handler.writeToDatabase(ifValues,agente);

                try{
                    Thread.sleep(5000);
                } catch(InterruptedException ie) {
                    System.out.println("INTERRUPTED!");
                }

                while(true) {
                    ifValues = new ArrayList<>();
                    for(int i = 1; i<=this.number_interfaces;i++) {
                        String ifIndex = String.valueOf(i);
                        String inOctets = getAsString(new OID("1.3.6.1.2.1.2.2.1.10." + i));
                        String outOctets = getAsString(new OID("1.3.6.1.2.1.2.2.1.16." + i));
                        String ifDescr = interfaces.getInterfacesInfo().get(i).getIfDescr();
                        String ifPhysAddress = interfaces.getInterfacesInfo().get(i).getIfPhysAddress();
                        HashMap<Integer,InterfaceValues> interfacesInfo = interfaces.getInterfacesInfo();
                        InterfaceValues iv = interfacesInfo.get(i);
                        long tempo = iv.getTempo_entre_consultas();
                        if(existsDifference(i,inOctets,outOctets)) {
                            if(tempo == 1000) {
                                singIfValues = new InterfaceValues(ifIndex,ifDescr,ifPhysAddress,inOctets,outOctets,tempo);
                            } else {
                                singIfValues = new InterfaceValues(ifIndex,ifDescr,ifPhysAddress,inOctets,outOctets,(tempo - 500));
                            }

                        }
                        else {
                            if(tempo == 10000) {
                                singIfValues = new InterfaceValues(ifIndex,ifDescr,ifPhysAddress,inOctets,outOctets,tempo);
                            } else {
                                singIfValues = new InterfaceValues(ifIndex,ifDescr,ifPhysAddress,inOctets,outOctets,(tempo + 500));
                            }
                        }
                        interfacesInfo.put(i,singIfValues);
                        ifValues.add(singIfValues);
                        //System.out.println("; Interface: " + i + "; TEMPO: " + tempo );
                    }
                    database_handler.writeToDatabase(ifValues,agente);
                    try{
                        Thread.sleep(5000);
                    } catch(InterruptedException ie) {
                        System.out.println("INTERRUPTED!");
                    }

                }
            } catch(IOException ioe) {
                System.out.println("Erro!");
            }

    }

    private boolean existsDifference(int ifIndex, String inOctets,String outOctets) {
        Long dif = Long.parseLong(outOctets) - Long.parseLong(inOctets);
        if(difOctets_map.get(ifIndex) == dif) return false;
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

    private String getIfDescrAsString(OID oid) throws IOException {
        ResponseEvent event = get(new OID[] {oid});
        if(event != null) {
            String s = event.getResponse().get(0).getVariable().toString();
            return s;

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

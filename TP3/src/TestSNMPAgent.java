import java.io.IOException;
import java.sql.SQLOutput;
import java.util.ArrayList;

import org.snmp4j.agent.ManagedObject;
import org.snmp4j.agent.mo.DefaultMOTable;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOTable;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.SMIConstants;

public class TestSNMPAgent {
    public SNMPAgent agent = null;
    public SNMPManager client = null;
    public String address;

    //static final OID sysDescr = new OID(".1.3.6.1.3.2019");
    static final OID indexParam = new OID("1.3.6.1.3.2019.1.1.0");
    static final OID nameParam = new OID("1.3.6.1.3.2019.1.2.0");
    static final OID flagParam = new OID("1.3.6.1.3.2019.1.3.0");


    public void runAgent(Agente agente) throws IOException {
        this.init(agente);
    }

        /**
         * Constructor
         * @param add
         */
    public TestSNMPAgent(String add) {
        address = add;
    }

    private void init(Agente agente) throws IOException {
        agent = new SNMPAgent("0.0.0.0/" + agente.getPorta());
        agent.start();
        // Since BaseAgent registers some mibs by default we need to unregister
        // one before we register our own sysDescr. Normally you would
        // override that method and register the mibs that you need
        agent.unregisterManagedObject(agent.getSnmpv2MIB());    

        // Register a system description, use one from you product environment
        // to test with
        ArrayList<String> images = agente.getImagens();
        MOTableBuilder builder = new MOTableBuilder(new OID("1.3.6.1.3.2019.2.1"));
        builder.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING, MOAccessImpl.ACCESS_READ_ONLY);
        for(int i = 0 ; i< images.size(); i++) {
            builder.adicionarValorEntrada(new OctetString(images.get(i)));
        }
        MOTable tabela_imagens = builder.build();
        agent.registerManagedObject(tabela_imagens);
        //agent.registerManagedObject(new OID("1.3.6.1.3.2019.2"),tabela_imagens);
        // Setup the client to use our newly started agent
        client = new SNMPManager("udp:127.0.0.1/"+agente.getPorta());
        //System.out.println(tabela_imagens.removeRow(new OID("1")));
        for(int i = 1; i <= images.size(); i++) {
            System.out.println(client.getAsString(new OID("1.3.6.1.3.2019.2.1.1." + i)));
        }

    }
}


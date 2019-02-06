import java.io.IOException;
import org.snmp4j.smi.OID;

public class TestSNMPAgent {
    public SNMPAgent agent = null;
    public SNMPManager client = null;
    public String address;

    static final OID sysDescr = new OID(".1.3.6.1.3.2019");
    public static void main(String[] args) throws IOException {
        TestSNMPAgent client = new TestSNMPAgent("udp:127.0.0.1/161");
        client.init();
    }

        /**
         * Constructor
         * @param add
         */
    public TestSNMPAgent(String add) {
        address = add;
    }

    private void init() throws IOException {
        agent = new SNMPAgent("0.0.0.0/2001");
        agent.start();
        // Since BaseAgent registers some mibs by default we need to unregister
        // one before we register our own sysDescr. Normally you would
        // override that method and register the mibs that you need
        agent.unregisterManagedObject(agent.getSnmpv2MIB());    

        // Register a system description, use one from you product environment
        // to test with
        agent.registerManagedObject(MOCreator.createReadOnly(sysDescr,"Teste agente ok!"));

        // Setup the client to use our newly started agent
        client = new SNMPManager("udp:127.0.0.1/2001");
        System.out.println(client.getAsString(sysDescr));

    }
}


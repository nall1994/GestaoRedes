import org.snmp4j.TransportMapping;
import org.snmp4j.agent.*;
import org.snmp4j.agent.mo.MOTableRow;
import org.snmp4j.agent.mo.snmp.*;
import org.snmp4j.agent.security.MutableVACM;
import org.snmp4j.mp.MPv3;
import org.snmp4j.security.*;
import org.snmp4j.smi.*;
import org.snmp4j.transport.TransportMappings;

import java.io.File;
import java.io.IOException;

public class SNMPAgent extends BaseAgent {
    private String address;

    public SNMPAgent(String address) throws IOException{
        /**
         * Creates a base agent with a DefaultMOServer as MOServer.
         * (Podemos modificar a implementação do servidor)
         **/
        super(new File("../ageConfs/conf.agent"), new File("../ageConfs/bootCounter.agent"),
                new CommandProcessor(new OctetString(MPv3.createLocalEngineID())));
        this.address = address;
    }

        /**
         * Adds community to security name mappings needed for SNMPv1 and SNMPv2c.
         **/
    @Override
    protected void addCommunities(SnmpCommunityMIB communityMIB) {

        Variable[] com2sec = new Variable[] {
                new OctetString("public"),
                new OctetString("cpublic"), // security name
                getAgent().getContextEngineID(), // local engine ID
                new OctetString("public"), // default context name
                new OctetString(), // transport tag
                new Integer32(StorageType.nonVolatile), // storage type
                new Integer32(RowStatus.active) // row status
        };

        MOTableRow row;
        row = communityMIB.getSnmpCommunityEntry().createRow(new OctetString("public2public").toSubIndex(true), com2sec);

        communityMIB.getSnmpCommunityEntry().addRow((SnmpCommunityMIB.SnmpCommunityEntryRow) row);

    }

    /**
     * Adds initial notification targets and filters.
     */

    @Override
    protected void addNotificationTargets(SnmpTargetMIB arg0, SnmpNotificationMIB arg1) {
        // TODO Auto-generated method stub
    }

        /**
         * Adds all the necessary initial users to the USM.
         */

    @Override
    protected void addUsmUser(USM arg0) {
    }

        /**
         * Adds initial VACM configuration.
         */

    @Override
    protected void addViews(VacmMIB vacm) {
        vacm.addGroup(SecurityModel.SECURITY_MODEL_SNMPv2c, new OctetString(
                        "cpublic"), new OctetString("v1v2group"), StorageType.nonVolatile);

        vacm.addAccess(new OctetString("v1v2group"), new OctetString("public"),
                SecurityModel.SECURITY_MODEL_ANY, SecurityLevel.NOAUTH_NOPRIV,
                MutableVACM.VACM_MATCH_EXACT, new OctetString("fullReadView"),
                new OctetString("fullWriteView"), new OctetString("fullNotifyView"), StorageType.nonVolatile);

        vacm.addViewTreeFamily(new OctetString("fullReadView"), new OID("1.3"), new OctetString(),
                                    VacmMIB.vacmViewIncluded, StorageType.nonVolatile);

    }

        /**
         * Unregister the basic MIB modules from the agent's MOServer.
         */

    @Override
    protected void unregisterManagedObjects() {
        // TODO Auto-generated method stub

    }

        /**
         * Register additional managed objects at the agent's server.
         */

    @Override
    protected void registerManagedObjects() {
        // TODO Auto-generated method stub
    }

    protected void initTransportMappings() throws IOException {
        transportMappings = new TransportMapping[1];
        Address addr = GenericAddress.parse(address);
        TransportMapping tm = TransportMappings.getInstance().createTransportMapping(addr);
        transportMappings[0] = tm;
    }

        /**
         * Start method invokes some initialization methods needed to start the agent
         * @throws IOException
         */

    public void start() throws IOException {
        init();
        // This method reads some old config from a file and cause
        // unexpected behavior.
        // loadConfig(ImportModes.REPLACE_CREATE);
        addShutdownHook();
        getServer().addContext(new OctetString("public"));
        finishInit();
        run();
        sendColdStartNotification();
    }

        /**
         * Clients can register the MO they need
         */

    public void registerManagedObject(ManagedObject mo) {
        try {
            server.register(mo, null);
        } catch (DuplicateRegistrationException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void unregisterManagedObject(MOGroup moGroup) {
        moGroup.unregisterMOs(server, getContext(moGroup));
    }

}

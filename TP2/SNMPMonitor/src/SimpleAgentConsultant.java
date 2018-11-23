import org.snmp4j.Snmp;

public class SimpleAgentConsultant extends Thread {
    private Agente agente;
    private Snmp snmp;
    private JSONWriterAndReader database_handler;

    public SimpleAgentConsultant(Agente agente) {
        this.agente = agente;
    }

    public void run() {

    }
}

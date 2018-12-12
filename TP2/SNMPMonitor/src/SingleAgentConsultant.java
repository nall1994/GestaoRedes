import org.snmp4j.Snmp;

public class SingleAgentConsultant extends Thread {
    private Agente agente;
    private Snmp snmp;
    private JSONWriterAndReader database_handler;

    public SingleAgentConsultant(Agente agente) {
        this.agente = agente;
    }

    public void run() {

    }
}

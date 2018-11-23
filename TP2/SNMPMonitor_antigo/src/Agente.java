public class Agente {
    private String ip;
    private String porta;

    public Agente(String ip,String porta) {
        this.ip = ip;
        this.porta = porta;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getPorta() {
        return porta;
    }

    public void setPorta(String porta) {
        this.porta = porta;
    }
}

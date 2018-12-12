import org.apache.commons.io.FileUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.snmp4j.*;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.*;
import org.apache.commons.io.FileUtils;
import org.snmp4j.transport.DefaultUdpTransportMapping;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.InputMismatchException;
import java.util.List;
import java.util.Scanner;

public class SNMPMonitor {

    private static String path_to_database = "../Database/";
    private static boolean pastMenu = false;
    private static Snmp snmp;

    public static void main(String[] args) {
        try{
            TransportMapping transport = new DefaultUdpTransportMapping();
            snmp = new Snmp(transport);
            transport.listen();
        } catch(Exception ex) {
            System.out.println("couldn't connect snmp session!");
        }

        Scanner s = new Scanner(System.in);
        int opcao;
        while(true) {
            if(pastMenu) break;
            System.out.println("Escolha a opção associada à acção que pretende realizar: ");
            System.out.println("1 - Executar o monitor.");
            System.out.println("2 - Acrescentar Agente SNMP.");
            System.out.println("3 - Remover Agente SNMP.");
            System.out.println("4 - Listar Agentes SNMP.");
            System.out.println("5 - Alterar configuração de interface.");
            System.out.println("6 - Terminar Monitor.");
            while(true) {
                try {
                    opcao = s.nextInt();
                    if(opcao > 0 && opcao < 7)
                        break;
                    else System.out.println("Introduza apenas o número 1, 2, 3, 4, 5 ou 6.");
                } catch(InputMismatchException ime) {
                    System.out.println("Introduza um número: 1, 2, 3, 4, 5 ou 6.");
                    s.nextLine();
                }
            }
            s.nextLine();
            String ip,porta;
            Agente agente;
            switch(opcao) {
                case 1:
                    executeAgents();
                    break;
                case 2:
                    System.out.println("Tenha atenção que a correta inserção do ip e da porta do agente é da sua responsabilidade!");
                    System.out.println("Insira o IP do Agente:");
                    ip = s.nextLine();
                    System.out.println("Insira a porta onde o Agente escuta:");
                    porta = s.nextLine();
                    agente = new Agente(ip,porta,0);
                    addAgentToConfigurationFile(agente);
                    break;
                case 3:
                    System.out.println("Insira o IP do Agente a remover:");
                    ip = s.nextLine();
                    System.out.println("Insira a porta onde o Agente a remover escuta:");
                    porta = s.nextLine();
                    agente = new Agente(ip,porta,0);
                    removeAgentFromConfigurationFile(agente);
                    break;
                case 4:
                    listAgents();
                    break;
                case 5:
                    //mudar configuração de interface.
                case 6:
                    System.exit(0);
                default:
                    break;

            }
        }

        while(true);

    }

    private static void executeAgents() {
        File file = new File( path_to_database + "agents.json");
        try {
            String content = FileUtils.readFileToString(file,"utf-8");
            JSONArray infoAgentes = new JSONArray(content);
            System.out.println("IP----------PORTA----------Número Interfaces");
            List<Agente> agentes = new ArrayList<>();
            for(int i = 0; i < infoAgentes.length();i++) {
                JSONObject agente = infoAgentes.getJSONObject(i);
                Agente a = new Agente(agente.getString("ipAgente"),agente.getString("portaAgente"),Integer.parseInt(agente.getString("numInterfaces")));
                agentes.add(a);
            }

            for(Agente a : agentes) {
                System.out.println("Consulting agent with IP:" + a.getIp() + " ; On port:" + a.getPorta());
                SingleAgentConsultant sac = new SingleAgentConsultant(a,path_to_database);
                sac.start();
            }

        }catch(IOException ioex) {
            System.out.println("could not read from agents's database file!");
        }

    }

    private static void addAgentToConfigurationFile(Agente agente) {
        File file = new File(path_to_database + "agents.json");
        try{
            String content = FileUtils.readFileToString(file,"utf-8");
            JSONArray infoAgentes = new JSONArray(content);
            JSONObject newAgent = new JSONObject();
            String numInterfaces = getAsString(new OID("1.3.6.1.2.1.2.1.0"),agente);
            agente.setNumber_interfaces(Integer.parseInt(numInterfaces));
            newAgent.put("ipAgente",agente.getIp());
            newAgent.put("portaAgente",agente.getPorta());
            newAgent.put("numInterfaces",String.valueOf(agente.getNumber_interfaces()));
            infoAgentes.put(newAgent);
            FileUtils.writeStringToFile(file,infoAgentes.toString(4),"utf-8",false);

            //Agente adicionado ao ficheiro agents.json, criar a sua diretoria e ficheiros de configuração
            String path_to_this_agent = path_to_database + agente.getIp() + "_" + agente.getPorta();
            System.out.println(path_to_this_agent);
            if(new File(path_to_this_agent).mkdir()) {
                for(int i = 1; i <= agente.getNumber_interfaces();i++) {
                    File file1 = new File(path_to_this_agent + "/interface" + i + ".json");
                    if(!file1.createNewFile()) System.out.println("Couldn't create interface " + i + " file.");
                }
            } else {
                System.out.println("Could not create the agent's repository");
            }

            //adding interface config file
            String path_to_config = path_to_database + "config/";
            Path file2 = Paths.get(path_to_config + agente.getIp() + "_" + agente.getPorta() + "_interfaces" + ".config");
            List<String> linesToWrite = new ArrayList<>();
            for(int i = 1; i <= agente.getNumber_interfaces();i++) {
                linesToWrite.add(i + ":fixed:3000");
            }
            Files.write(file2,linesToWrite, Charset.forName("UTF-8"));
        }catch(IOException ioex){
            System.out.println("Could not read from agents's database file!");
        }
    }

    private static void removeAgentFromConfigurationFile(Agente agente) {
        File file = new File(path_to_database + "agents.json");
        try{
            String content = FileUtils.readFileToString(file,"utf-8");
            JSONArray infoAgentes = new JSONArray(content);
            JSONArray newInfoAgentes = new JSONArray();
            for(int i = 0; i < infoAgentes.length();i++) {
                JSONObject obj = infoAgentes.getJSONObject(i);
                if(!((obj.getString("ipAgente").equalsIgnoreCase(agente.getIp())) && (obj.getString("portaAgente").equalsIgnoreCase(agente.getPorta())))) {
                    newInfoAgentes.put(obj);
                }
            }
            FileUtils.writeStringToFile(file,newInfoAgentes.toString(4),"utf-8",false);

            File targetDirectory = new File(path_to_database + agente.getIp() + "_" + agente.getPorta());
            if(targetDirectory.exists())
                FileUtils.deleteDirectory(targetDirectory);

            //File targetConfig = new File( "Database/config/" +  agente.getIp() + "_" + agente.getPorta() + "_interfaces.conf");
            //FileUtils.forceDelete(targetConfig);
            //Remoção do ficheiro dentro da pasta config nao está a funcionar

        }catch(IOException ioex){
            System.out.println("Could not read from agents's database file!");
        }
    }


    private static void listAgents() {
        File file = new File( path_to_database + "agents.json");
        try {
            String content = FileUtils.readFileToString(file,"utf-8");
            JSONArray infoAgentes = new JSONArray(content);
            System.out.println("IP----------PORTA----------Número Interfaces");
            for(int i = 0; i < infoAgentes.length();i++) {
                JSONObject agente = infoAgentes.getJSONObject(i);
                System.out.println(agente.getString("ipAgente") + "---" + agente.getString("portaAgente") + "-------------" + agente.getString("numInterfaces"));
            }

        }catch(IOException ioex) {
            System.out.println("could not read from agents's database file!");
        }
    }

    private static String getAsString(OID oid, Agente agente) throws IOException {
        ResponseEvent event = get(new OID[] {oid},agente);
        if(event != null) {
            return event.getResponse().get(0).getVariable().toString();
        } else {
            return "";
        }

    }

    private static ResponseEvent get(OID oids[],Agente agente) throws IOException {
        PDU pdu = new PDU();
        for(OID oid : oids) {
            pdu.add(new VariableBinding(oid));
        }
        pdu.setType(PDU.GET);
        ResponseEvent event = snmp.send(pdu,getTarget(agente),null);
        return event;
    }

    private static Target getTarget(Agente agente) {
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

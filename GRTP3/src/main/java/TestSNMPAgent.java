import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Scanner;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.core.DockerClientBuilder;
import com.github.dockerjava.api.*;
import org.snmp4j.agent.ManagedObject;
import org.snmp4j.agent.mo.DefaultMOTable;
import org.snmp4j.agent.mo.MOAccessImpl;
import org.snmp4j.agent.mo.MOTable;
import org.snmp4j.smi.Integer32;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.SMIConstants;

public class TestSNMPAgent {
    public SNMPAgent agent = null;
    public SNMPManager client = null;
    public String address;
    private int numberImages;
    private int nContainers;

    static final OID indexParam = new OID("1.3.6.1.3.2019.1.1.0");
    static final OID nameParam = new OID("1.3.6.1.3.2019.1.2.0");
    static final OID flagParam = new OID("1.3.6.1.3.2019.1.3.0");
    static final OID indexIParam = new OID("1.3.6.1.3.2019.1.4.0");


    public void runAgent(Agente agente, int numberImages) throws IOException {
        this.numberImages = numberImages;
        this.init(agente);
    }

        /**
         * Constructor
         * @param add
         */
    public TestSNMPAgent(String add) {
        address = add;
        nContainers = 0;
    }

    private void init(Agente agente) throws IOException {
        agent = new SNMPAgent("0.0.0.0/" + agente.getPorta());
        agent.start();
        agent.unregisterManagedObject(agent.getSnmpv2MIB());
        ArrayList<String> images = agente.getImagens();

        MOTableBuilder builder = new MOTableBuilder(new OID("1.3.6.1.3.2019.2.1"));
        builder.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING, MOAccessImpl.ACCESS_READ_ONLY);
        for(int i = 0 ; i< images.size(); i++) {
            builder.adicionarValorEntrada(new OctetString(images.get(i)));
        }
        MOTable tabela_imagens = builder.build();

        MOTableBuilder builder_c = new MOTableBuilder(new OID("1.3.6.1.3.2019.3.1"));
        builder_c.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING,MOAccessImpl.ACCESS_READ_ONLY);
        builder_c.adicionarColuna(SMIConstants.SYNTAX_INTEGER32,MOAccessImpl.ACCESS_READ_ONLY);
        builder_c.adicionarColuna(SMIConstants.SYNTAX_INTEGER32,MOAccessImpl.ACCESS_READ_WRITE);
        builder_c.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING,MOAccessImpl.ACCESS_READ_WRITE);

        agent.registerManagedObject(MOScalarCreator.createWriteRead(indexParam,"None"));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(nameParam,"None"));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(flagParam,0));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(indexIParam,1));
        agent.registerManagedObject(tabela_imagens);

        MOTable tabela_containers = builder_c.build();
        agent.registerManagedObject(tabela_containers);

        // Setup the client to use our newly started agent
        client = new SNMPManager("udp:127.0.0.1/"+agente.getPorta());
        boolean terminateSystem = false;
        Scanner scanner = new Scanner(System.in);
        while(!terminateSystem) {
            printMenu();
            int escolha = scanner.nextInt();
            switch(escolha) {
                case 1:
                    printContainersMenu(client);
                    int containerIndex = scanner.nextInt();
                    //Ir buscar info da imagem com o containerIndex.
                    String imagem_escolhida = client.getAsString(new OID("1.3.6.1.3.2019.2.1.1." + containerIndex));
                    System.out.println("A criar container com a imagem: " + imagem_escolhida + ".....");
                    client.setValueString(indexParam,imagem_escolhida);
                    client.setValueString(nameParam,imagem_escolhida);
                    client.setValueInt(indexIParam,containerIndex);
                    break;
                case 2:
                    String testeParam = client.getAsString(indexParam);
                    String nomeContainer = client.getAsString(nameParam);
                    int indexImage = Integer.parseInt(client.getAsString(indexIParam));
                    Runtime.getRuntime().exec("clear");

                    if(testeParam.equals("None")) {
                        System.out.println("Nenhum container está carregado!");
                    } else {
                        int index_inTable = Integer.parseInt(client.getAsString(indexIParam));
                        System.out.println("=============");
                        System.out.println("Informações Atuais no container");
                        System.out.println("Nome do Container: "+ testeParam + " com o indice "+ index_inTable +" na tabela de imagens.");
                        System.out.println("=============");
                        builder_c.adicionarEntrada(tabela_containers,nomeContainer,indexImage,1,"0%");
                        client.setValueString(indexParam,"None");
                        client.setValueString(nameParam,"None");
                        client.setValueInt(indexIParam,0);
                        criarContainer(testeParam,nomeContainer,builder_c,tabela_containers);

                    }
                    break;
                case 3:
                    terminateSystem = true;
                    verificarContainers();
                    break;
                default:
                    System.out.println("Escolha não reconhecida!");
            }
        }
        System.exit(0);

    }

    private void printMenu() {
        System.out.println("-----CONTAINERS MENU-----");
        System.out.println("Escolha a sua opção (1,2 ou 3):");
        System.out.println("1 - Carregar parâmetros de um container.");
        System.out.println("2 - Criar o container carregado.");
        System.out.println("3 - Sair do sistema.");
    }

    private void printContainersMenu(SNMPManager client) {
        try {
            System.out.println("Escolha o container do qual quer carregar os parâmetros para criação:");
            for(int i = 1; i <= numberImages;i++) {
                System.out.println(i + " - " + client.getAsString(new OID("1.3.6.1.3.2019.2.1.1." + i)));
            }
        } catch(IOException e) {
            e.printStackTrace();
        }
    }


    private void criarContainer(String imagem, String nomeContainer,MOTableBuilder tabela,MOTable tabelaA) throws IOException{
        ProcessBuilder builder = new ProcessBuilder("docker","create",imagem);
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        String tempLine="";
        while (true){
            line = r.readLine();
            if(line!=null) {
                tempLine = line;

            } else {
                break;
            }
            System.out.println(line);
        }
        nContainers++;
        System.out.println("TEMP LINE: " + tempLine);
        ProcessBuilder correr = new ProcessBuilder("docker","run","--detach",tempLine);
        correr.redirectErrorStream(true);
        Process p1 = correr.start();
        OID statusOID = new OID("1.3.6.1.3.2019.3.1.3." + nContainers);
        tabela.atualizarEstado(new OID(""+ nContainers),3,tabelaA);
        System.out.println("Acessing: " + statusOID + " value -> "+client.getAsString(statusOID));
        System.out.println(client.getAsString(nameParam));
    }

    private void verificarContainers() throws IOException{
        ProcessBuilder builder = new ProcessBuilder("docker","ps","-a");
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        while (true){
            line = r.readLine();
            if(line==null) break;
            System.out.println(line);
        }
    }

}


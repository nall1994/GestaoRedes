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

public class SNMPAgentFunctions {
    public SNMPAgent agent = null;
    public SNMPManager client = null;
    public String address;
    private int numberImages;
    private int nContainers;
    private MOTableBuilder builder_images_table;
    private MOTableBuilder builder_containers_table;
    private MOTable images_table;
    private MOTable containers_table;
    static final OID indexParam = new OID("1.3.6.1.3.2019.1.1.0");
    static final OID nameParam = new OID("1.3.6.1.3.2019.1.2.0");
    static final OID flagParam = new OID("1.3.6.1.3.2019.1.3.0");
    static final OID indexIParam = new OID("1.3.6.1.3.2019.1.4.0");

    public SNMPManager getClient() {
        return this.client;
    }

    public void runAgent(Agente agente, int numberImages) throws IOException {
        this.numberImages = numberImages;
        this.init(agente);
    }

        /**
         * Constructor
         * @param add
         */
    public SNMPAgentFunctions(String add) {
        address = add;
        nContainers = 0;
    }

    private void init(Agente agente) throws IOException {
        agent = new SNMPAgent("0.0.0.0/" + agente.getPorta());
        agent.start();
        agent.unregisterManagedObject(agent.getSnmpv2MIB());
        ArrayList<String> images = agente.getImagens();

        builder_images_table = new MOTableBuilder(new OID("1.3.6.1.3.2019.2.1"));
        builder_images_table.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING, MOAccessImpl.ACCESS_READ_ONLY);
        for(int i = 0 ; i< images.size(); i++) {
            builder_images_table.adicionarValorEntrada(new OctetString(images.get(i)));
        }
        images_table = builder_images_table.build();

        builder_containers_table = new MOTableBuilder(new OID("1.3.6.1.3.2019.3.1"));
        builder_containers_table.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING,MOAccessImpl.ACCESS_READ_ONLY);
        builder_containers_table.adicionarColuna(SMIConstants.SYNTAX_INTEGER32,MOAccessImpl.ACCESS_READ_ONLY);
        builder_containers_table.adicionarColuna(SMIConstants.SYNTAX_INTEGER32,MOAccessImpl.ACCESS_READ_WRITE);
        builder_containers_table.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING,MOAccessImpl.ACCESS_READ_WRITE);
        builder_containers_table.adicionarColuna(SMIConstants.SYNTAX_OCTET_STRING,MOAccessImpl.ACCESS_READ_WRITE);

        agent.registerManagedObject(MOScalarCreator.createWriteRead(indexParam,"None"));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(nameParam,"None"));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(flagParam,0));
        agent.registerManagedObject(MOScalarCreator.createWriteRead(indexIParam,1));
        agent.registerManagedObject(images_table);

        containers_table = builder_containers_table.build();
        agent.registerManagedObject(containers_table);

        // Setup the client to use our newly started agent
        client = new SNMPManager("udp:127.0.0.1/"+agente.getPorta());

    }

    public String getCreatedContainers() {
        String result = "";
        try {
            for (int i = 1; i<=nContainers;i++) {
                String containerName = client.getAsString(new OID("1.3.6.1.3.2019.3.1.1." + i));
                String containerID = client.getAsString(new OID("1.3.6.1.3.2019.3.1.5." + i));
                if(i < nContainers) {
                    result += containerName + " -> " + containerID + "\n";
                } else {
                    result += containerName + " -> " + containerID;
                }

            }
        }catch(IOException ex) {
            result = "Ocorreu uma exceção! Tente novamente!";
        }
        return result;
    }

    public String removeContainer(int container_index) {
        String result = "";
        try {
            String containerID = client.getAsString(new OID("1.3.6.1.3.2019.3.1.5." + container_index));
            ProcessBuilder pb = new ProcessBuilder("docker","container","stop",containerID);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            ProcessBuilder pb2 = new ProcessBuilder("docker","container","rm",containerID);
            pb2.redirectErrorStream(true);
            Process p2 = pb2.start();
            result = "Container com ID: " + containerID + " removido com sucesso!";
        }catch(IOException e) {
            result = "Ocorreu uma exceção! Tente novamente!";
        }

        return result;
    }

    public String loadParameters(int image_index) {
        String returnResult = "";
        try {
            String imagem_escolhida = client.getAsString(new OID("1.3.6.1.3.2019.2.1.1." + image_index));
            client.setValueString(indexParam,imagem_escolhida);
            client.setValueString(nameParam,imagem_escolhida);
            client.setValueInt(indexIParam,image_index);
            returnResult = "Parâmetros carregados! Já pode criar o container!";
        }catch(IOException e) {
            returnResult = "Uma exceção ocorreu! Tente novamente por favor!";
        }

        return returnResult;
    }

    public String create() {
        String result = "";
        try {
            String testeParam = client.getAsString(indexParam);
            String nomeContainer = client.getAsString(nameParam);
            if(testeParam.equalsIgnoreCase("None")) {
                result = "Tem que carregar os parâmetros de uma imagem primeiro!";
            } else {
                int indexImage = Integer.parseInt(client.getAsString(indexIParam));
                builder_containers_table.adicionarEntrada(containers_table,nomeContainer,indexImage,1,"0%","changing");
                client.setValueString(indexParam,"None");
                client.setValueString(nameParam,"None");
                client.setValueInt(indexIParam,0);
                result = criarContainer(testeParam, nomeContainer, builder_containers_table, containers_table);
            }
        }catch(IOException e) {
            result = "Ocorreu uma exceção! Tente novamente por favor!";
        }


        return result;
    }


    private String criarContainer(String imagem, String nomeContainer,MOTableBuilder tabelaBuilder,MOTable tabela) throws IOException{
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
        ProcessBuilder correr = new ProcessBuilder("docker","run","--detach",tempLine);
        correr.redirectErrorStream(true);
        Process p1 = correr.start();
        OID statusOID = new OID("1.3.6.1.3.2019.3.1.3." + nContainers);
        tabelaBuilder.atualizarEstado(new OID(""+ nContainers),3,tabela);
        tabelaBuilder.atualizarID(new OID("" + nContainers),tempLine,tabela);
        return tempLine;
    }

    public String listContainers() {
        try {
            return verificarContainers();
        } catch(IOException e ){
            return "Ocorreu uma exceção! Tente novamente por favor!";
        }

    }

    private String verificarContainers() throws IOException{
        ProcessBuilder builder = new ProcessBuilder("docker","ps","-a");
        builder.redirectErrorStream(true);
        Process p = builder.start();
        BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
        String line;
        String result = "";
        while (true){
            line = r.readLine();
            if(line==null) break;
            result += line + "\n";
        }
        return result;
    }

}


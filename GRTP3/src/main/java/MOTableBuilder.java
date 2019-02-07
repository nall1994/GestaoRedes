import java.util.ArrayList;
import java.util.List;

import org.snmp4j.agent.MOAccess;
import org.snmp4j.agent.mo.*;
import org.snmp4j.smi.*;

public class MOTableBuilder {
    private MOTableSubIndex[] subIndices = new MOTableSubIndex[] {new MOTableSubIndex(SMIConstants.SYNTAX_INTEGER)};
    private MOTableIndex indexDef = new MOTableIndex(subIndices,false);
    private final List<MOColumn> colunas = new ArrayList<>();
    private final List<Variable[]> linhas = new ArrayList<>();
    private int linha_atual = 0;
    private int coluna_atual = 0;

    private OID raiz_tabela;

    private int contador_tipo_coluna = 0;

    public MOTableBuilder(OID oid) {
        raiz_tabela = oid;
    }

    public MOTableBuilder adicionarColuna(int syntax,MOAccess acesso) {
        contador_tipo_coluna++;
        MOColumn nova;
        colunas.add(nova = new MOColumn(contador_tipo_coluna,syntax,acesso));
        System.out.println(nova.getAccess());
        return this;
    }

    public MOTableBuilder adicionarValorEntrada(Variable variavel) {
        if(linhas.size() == linha_atual) {
            linhas.add(new Variable[colunas.size()]);
        }
        linhas.get(linha_atual)[coluna_atual] = variavel;
        coluna_atual++;
        if(coluna_atual >= colunas.size()) {
            linha_atual++;
            coluna_atual = 0;
        }
        return this;
    }

    public MOTable build() {
        DefaultMOTable tabela = new DefaultMOTable(raiz_tabela,indexDef,colunas.toArray(new MOColumn[0]));
        MOMutableTableModel modelo = (MOMutableTableModel) tabela.getModel();
        int i = 1;
        for(Variable[] variables : linhas) {
            modelo.addRow(new DefaultMOMutableRow2PC(new OID(String.valueOf(i)),variables));
            System.out.println(modelo.getRow(new OID("1")));
            System.out.println("Linha " + i + " - " +variables[0].toString());
            i++;
        }
        tabela.setVolatile(false);
        return tabela;
    }

    public MOTable atualizarEstado(OID oid, int newState, MOTable tabela){
        MOMutableTableModel modelo = (MOMutableTableModel) tabela.getModel();
        Variable[] variables = new Variable[modelo.getRow(oid).size()];
        for(int i=0;i<modelo.getRow(oid).size();i++){
            variables[i] = modelo.getRow(oid).getValue(i);
            System.out.println(variables[i]);
        }
        variables[2]= new Integer32(newState);
        modelo.removeRow(oid);
        modelo.addRow(new DefaultMOMutableRow2PC(oid,variables));
        return tabela;
    }

    public void adicionarEntrada(MOTable tabela,String nomeParam,int indexI,int status, String cpu ){
        MOMutableTableModel modelo = (MOMutableTableModel) tabela.getModel();
        int nRows = modelo.getRowCount()+1;
        Variable [] variables = new Variable[]{new OctetString(nomeParam),new Integer32(indexI),new Integer32(status), new OctetString(cpu)};
        modelo.addRow(new DefaultMOMutableRow2PC(new OID(""+ nRows),variables));

    }
}

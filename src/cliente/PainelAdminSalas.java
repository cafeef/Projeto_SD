package cliente;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import ui.Estilo;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import java.awt.BorderLayout;
import java.awt.Component;
import java.util.HashMap;
import java.util.Map;

/**
 * A aba de administração de salas: o CRUD dos itens d, e, f e g da EP-2.
 *
 * Mora numa aba só dela, e não embaixo da consulta, porque antes as duas
 * dividiam a mesma tela: a tabela ficava com três linhas visíveis, o
 * formulário espremido no rodapé, e a roda do mouse sobre a tabela rolava a
 * tabela em vez da página -- o formulário embaixo era inalcançável.
 *
 * Aqui a tabela é a única coisa que cresce, e o formulário abre numa janela
 * quando é chamado.
 */
final class PainelAdminSalas extends JPanel {

    private static final long serialVersionUID = 1L;

    private final transient Canal canal;

    private final JTable tabela = Estilo.tabela("ID", "Nome", "Capacidade", "Local",
            "Recursos", "Funciona em", "Estado");
    private final JButton botaoListar = new JButton("Atualizar lista");
    private final JButton botaoCadastrar = Estilo.botaoPrincipal("Cadastrar sala");
    private final JButton botaoEditar = new JButton("Editar sala");
    private final JButton botaoExcluir = Estilo.botaoDestrutivo("Excluir sala");

    /**
     * Os códigos de horário de cada sala, por room_id.
     *
     * A coluna mostra "terça 13:00-15:30", que se lê mas não se traduz de
     * volta sem ambiguidade. Guardar os códigos como vieram é o que permite o
     * formulário marcar exatamente os horários da sala escolhida.
     */
    private final transient Map<String, String> codigosDeHorario = new HashMap<>();

    PainelAdminSalas(Canal canal) {
        this.canal = canal;
        montar();
        ligarAcoes();
    }

    private void montar() {
        setLayout(new BorderLayout(0, 10));

        JScrollPane rolagem = new JScrollPane(tabela);

        JPanel acoes = new JPanel();
        acoes.setLayout(new BoxLayout(acoes, BoxLayout.X_AXIS));
        acoes.add(botaoListar);
        acoes.add(Box.createHorizontalStrut(8));
        acoes.add(botaoCadastrar);
        acoes.add(Box.createHorizontalStrut(8));
        acoes.add(botaoEditar);
        acoes.add(Box.createHorizontalGlue());
        acoes.add(botaoExcluir);

        add(Estilo.rodape("Selecione uma sala na tabela para editar ou excluir."),
                BorderLayout.NORTH);
        add(rolagem, BorderLayout.CENTER);
        add(acoes, BorderLayout.SOUTH);
    }

    private void ligarAcoes() {
        botaoListar.addActionListener(e -> listar(true));
        botaoCadastrar.addActionListener(e -> cadastrar());
        botaoEditar.addActionListener(e -> editar());
        botaoExcluir.addActionListener(e -> excluir());
    }

    void habilitar(boolean habilitado) {
        botaoListar.setEnabled(habilitado);
        botaoCadastrar.setEnabled(habilitado);
        botaoEditar.setEnabled(habilitado);
        botaoExcluir.setEnabled(habilitado);
    }

    void limpar() {
        Estilo.modeloDe(tabela).setRowCount(0);
        codigosDeHorario.clear();
    }

    // ------------------------------------------------------------------ ações

    private void listar(boolean avisarSeVazio) {
        canal.trocar(PainelSalas.requisicaoDoCatalogo(canal.token()), resposta -> {
            if (!"200".equals(campo(resposta, "status"))) {
                canal.informar(resposta);
                return;
            }
            preencherTabela(resposta, avisarSeVazio);
        });
    }

    /** Item d: cadastrar sala, com os horários marcados na grade de blocos. */
    private void cadastrar() {
        FormularioDeSala form = new FormularioDeSala();
        if (!form.mostrar(this, "Cadastrar sala", false)) {
            return;
        }
        canal.trocar(requisicaoDeCadastro(canal.token(), form.nome(), form.capacidade(),
                form.local(), form.recursos(), form.horarios()), resposta -> {
            canal.informar(resposta);
            if ("201".equals(campo(resposta, "status"))) {
                listar(false);
            }
        });
    }

    /** Item f: alterar a sala selecionada. */
    private void editar() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            canal.erro("Selecione uma sala na tabela para editar.");
            return;
        }
        var modelo = Estilo.modeloDe(tabela);
        String id = texto(modelo.getValueAt(linha, 0));

        FormularioDeSala form = new FormularioDeSala();
        form.preencher(texto(modelo.getValueAt(linha, 1)), texto(modelo.getValueAt(linha, 2)),
                texto(modelo.getValueAt(linha, 3)), texto(modelo.getValueAt(linha, 4)),
                codigosDeHorario.get(id), texto(modelo.getValueAt(linha, 6)));

        if (!form.mostrar(this, "Editar sala " + id, true)) {
            return;
        }
        canal.trocar(requisicaoDeAlteracao(canal.token(), id, form.nome(), form.capacidade(),
                form.local(), form.recursos(), form.estado(), form.horarios()), resposta -> {
            canal.informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
                listar(false);
            }
        });
    }

    /** Item g: excluir, sempre com confirmação -- a remoção não se desfaz. */
    private void excluir() {
        int linha = tabela.getSelectedRow();
        if (linha < 0) {
            canal.erro("Selecione uma sala na tabela para excluir.");
            return;
        }
        var modelo = Estilo.modeloDe(tabela);
        String id = texto(modelo.getValueAt(linha, 0));
        String nome = texto(modelo.getValueAt(linha, 1));

        int escolha = JOptionPane.showConfirmDialog(this,
                "Excluir a sala \"" + nome + "\"?\n\n"
                        + "Sala com reserva ativa não é removida — para tirá-la de"
                        + " circulação, mude o estado para \"inactive\".",
                "Excluir sala", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (escolha != JOptionPane.YES_OPTION) {
            return;
        }
        JsonObject req = Canal.requisicao("delete_room");
        req.addProperty("token", canal.token());
        req.addProperty("room_id", id);
        canal.trocar(req, resposta -> {
            canal.informar(resposta);
            if ("200".equals(campo(resposta, "status"))) {
                listar(false);
            }
        });
    }

    private void preencherTabela(JsonObject resposta, boolean avisarSeVazio) {
        var modelo = Estilo.modeloDe(tabela);
        modelo.setRowCount(0);
        codigosDeHorario.clear();
        JsonArray salas = resposta.has("rooms") && resposta.get("rooms").isJsonArray()
                ? resposta.getAsJsonArray("rooms")
                : new JsonArray();
        for (JsonElement elemento : salas) {
            JsonObject sala = elemento.getAsJsonObject();
            codigosDeHorario.put(campo(sala, "room_id"),
                    PainelSalas.listaParaTexto(sala, "slots"));
            modelo.addRow(new Object[] {
                    campo(sala, "room_id"),
                    campo(sala, "name"),
                    campo(sala, "capacity"),
                    campo(sala, "location"),
                    PainelSalas.listaParaTexto(sala, "resources"),
                    PainelSalas.horariosResumidos(sala),
                    "active".equals(campo(sala, "room_status")) ? "ativa" : "inativa"});
        }
        if (modelo.getRowCount() == 0 && avisarSeVazio) {
            JOptionPane.showMessageDialog(this, "Nenhuma sala cadastrada ainda.",
                    "Salas", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    // ------------------------------------------------- montagem das requisições

    static JsonObject requisicaoDeCadastro(String token, String nome, String capacidade,
                                           String local, String recursos, String horarios) {
        JsonObject req = Canal.requisicao("create_room");
        req.addProperty("token", token);
        req.addProperty("name", nome.trim());
        req.addProperty("capacity", capacidade.trim());
        req.addProperty("location", local.trim());
        req.add("resources", recursosDeTexto(recursos));
        req.add("slots", slotsDeTexto(horarios));
        return req;
    }

    /**
     * Monta o update_room com as seis chaves que o protocolo exige.
     *
     * Regra 9.7 (v2.9): no update_room, 'resources' vazio significa "não
     * alterar", e quem apaga todos é ["0"]. O campo chega preenchido com os
     * recursos da sala, então apagá-lo é um pedido deliberado -- e é esse
     * pedido que mandamos.
     */
    static JsonObject requisicaoDeAlteracao(String token, String roomId, String nome,
                                            String capacidade, String local, String recursos,
                                            String estado, String horarios) {
        JsonObject req = Canal.requisicao("update_room");
        req.addProperty("token", token);
        req.addProperty("room_id", roomId);
        req.addProperty("name", nome.trim());
        req.addProperty("capacity", capacidade.trim());
        req.addProperty("location", local.trim());
        JsonArray lista = recursosDeTexto(recursos);
        if (lista.size() == 0) {
            lista.add(protocolo.Validador.APAGAR_RECURSOS);
        }
        req.add("resources", lista);
        req.addProperty("room_status", estado);
        // Regra 8.7: lista vazia mantém os horários atuais.
        req.add("slots", slotsDeTexto(horarios));
        return req;
    }

    static JsonArray recursosDeTexto(String texto) {
        JsonArray lista = new JsonArray();
        for (String parte : texto.split(",")) {
            String limpo = parte.trim();
            // "tv,,projetor" digitado às pressas não vira recurso de nome vazio.
            if (!limpo.isEmpty()) {
                lista.add(limpo);
            }
        }
        return lista;
    }

    static JsonArray slotsDeTexto(String texto) {
        JsonArray lista = new JsonArray();
        for (String parte : texto.split(",")) {
            String limpo = parte.trim().toUpperCase();
            if (!limpo.isEmpty()) {
                lista.add(limpo);
            }
        }
        return lista;
    }

    private static String campo(JsonObject o, String chave) {
        return o != null && o.has(chave) && !o.get(chave).isJsonNull()
                ? o.get(chave).getAsString()
                : null;
    }

    private static String texto(Object valor) {
        return valor == null ? "" : valor.toString();
    }
}

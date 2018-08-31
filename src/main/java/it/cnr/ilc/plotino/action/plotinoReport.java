/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.cnr.ilc.plotino.action;

import com.hp.hpl.jena.ontology.Individual;
import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.query.Query;
import com.hp.hpl.jena.query.QueryExecution;
import com.hp.hpl.jena.query.QueryExecutionFactory;
import com.hp.hpl.jena.query.QueryFactory;
import com.hp.hpl.jena.query.QuerySolution;
import com.hp.hpl.jena.query.ResultSet;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.pdf.PdfWriter;
import it.cnr.ilc.plotino.model.OntoResult;
import it.cnr.ilc.plotino.model.plotinoModel;
import it.cnr.ilc.plotino.util.Nodo;
import it.cnr.ilc.plotino.util.PDFCreator;
import it.cnr.ilc.plotino.util.Relazione;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.PostConstruct;
import javax.faces.view.ViewScoped;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.primefaces.model.DefaultStreamedContent;
import org.primefaces.model.StreamedContent;

@Named
@ViewScoped
public class plotinoReport implements Serializable {

    @Inject
    private transient plotinoModel plotinoModel;

    // VARIABILI //
    private final String NAMESPACES = "PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> "
            + "PREFIX plotino: <http://www.semanticweb.org/ontologies/2014/4/8/Plotino1.owl#> "
            + "PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#> "
            + "PREFIX owl: <http://www.w3.org/2002/07/owl#> ";
    private String entryReport = "";
    private static Map<String, String> instanceMap;
    private static List<OntoResult> inferredRes = new ArrayList<OntoResult>();
    private static List<OntoResult> cleanedRes = new ArrayList<OntoResult>();
    private String result = "";
    private String visualizedRelations = "all";

    public String getVisualizedRelations() {
        return visualizedRelations;
    }

    public void setVisualizedRelations(String visualizedRelations) {
        this.visualizedRelations = visualizedRelations;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    static {
        instanceMap = new LinkedHashMap<String, String>();
    }

    private OntoResult selectedTableRow;

    public String getEntryReport() {
        return entryReport;
    }

    public void setEntryReport(String entryReport) {
        this.entryReport = entryReport;
    }

    public Map<String, String> getInstances() {
        return instanceMap;
    }

    public List<OntoResult> getInferredRes() {
        return inferredRes;
    }

    @PostConstruct
    private void loadPlotinoModel() {
        try {
            initializeReport();
            setEntryReport("Being");
            runReport();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void initializeReport() throws IOException {
        String queryString, inst;
        instanceMap.clear();
        queryString = NAMESPACES
                + "SELECT ?termine "
                + "WHERE { ?termine a owl:Thing . } "
                + "ORDER BY ?termine";
        Query query = QueryFactory.create(queryString);
        QueryExecution qe = QueryExecutionFactory.create(query, plotinoModel.getModel());
        for (ResultSet rs = qe.execSelect(); rs.hasNext();) {
            QuerySolution binding = rs.nextSolution();
            inst = binding.get("termine").toString().split("#")[1];
            instanceMap.put(inst, inst);
        }
        qe.close();
    }

    public void runReport() throws IOException {
        String value, prop;
        String nodes = getEntryReport() + "*", edges = "";
        cleanedRes.clear();
        inferredRes.clear();
        result = "";
        String queryString = NAMESPACES
                + "SELECT DISTINCT ?prop ?value "
                + "WHERE { plotino:" + getEntryReport() + " ?prop ?value . }";
        Query query = QueryFactory.create(queryString);
        QueryExecution qe = QueryExecutionFactory.create(query,
                plotinoModel.getModel());
        QueryExecution qe2 = QueryExecutionFactory.create(query,
                plotinoModel.getClearModel());
        // verifica delle triple inferite tramite interrogazione al modello semplice
        for (ResultSet rs2 = qe2.execSelect(); rs2.hasNext();) {
            QuerySolution binding = rs2.nextSolution();
            prop = binding.get("prop").toString().split("#")[1];
            value = binding.get("value").toString().contains("#") ? binding.get("value").toString().split("#")[1] : binding.get("value").toString();

            if (allowedTriple(prop, value)) {
                OntoResult cleanOntoRes = new OntoResult();
                cleanOntoRes.setTermine(getEntryReport());
                cleanOntoRes.setRelazione(prop);
                cleanOntoRes.setTermine_target(value);
                cleanedRes.add(cleanOntoRes);
                if ((getVisualizedRelations().equals("clean")) || (getVisualizedRelations().equals("all"))) {
                    nodes = nodes + value + "*";
                    edges = edges + prop + "*";
                }
            }
        }
        if (getVisualizedRelations().equals("clean")) {
            nodes = nodes.substring(0, nodes.lastIndexOf("*"));
            edges = edges.substring(0, edges.lastIndexOf("*"));
            result = nodes + "-" + edges;
        }
        qe2.close();
        System.out.println("CLEAN SIZE: " + cleanedRes.size());
        for (ResultSet rs = qe.execSelect(); rs.hasNext();) {
            QuerySolution binding = rs.nextSolution();
            prop = binding.get("prop").toString().split("#")[1];
            value = binding.get("value").toString().contains("#") ? binding.get("value").toString().split("#")[1] : binding.get("value").toString();

            if (allowedTriple(prop, value)) {
                OntoResult inferredOntoRes = new OntoResult();
                inferredOntoRes.setTermine(getEntryReport());
                inferredOntoRes.setRelazione(prop);
                inferredOntoRes.setTermine_target(value);
                inferredRes.add(inferredOntoRes);
            }
        }
        qe.close();
        // setta le triple inferite ed il loro ID
        int id = 1;
        for (OntoResult e : inferredRes) {
            e.setID(id++);
            if (inferred(e)) {
                e.setInferita("si");
                if ((getVisualizedRelations().equals("inf")) || (getVisualizedRelations().equals("all"))) {
                    nodes = nodes + e.getTermine_target() + "*";
                    edges = edges + e.getRelazione() + "*";
                }
            }
        }
        if ((getVisualizedRelations().equals("inf")) || (getVisualizedRelations().equals("all"))) {
            nodes = nodes.substring(0, nodes.lastIndexOf("*"));
            edges = edges.substring(0, edges.lastIndexOf("*"));
            result = nodes + "-" + edges;
        }
    }

    private boolean inferred(OntoResult e) {
        for (OntoResult or : cleanedRes) {
            if (or.getRelazione().equals(e.getRelazione()) && (or.getTermine_target().equals(e.getTermine_target()))) {
                return false;
            }
        }
        return true;
    }

    private boolean allowedTriple(String prop, String value) {
        if ((prop.equals("Ontological_Relation")) || (prop.contains("comment")) || (prop.contains("differentFrom"))
                || (value.equals("NamedIndividual")) || (value.equals("Resource")) || (value.equals("Thing"))) {
            return false;
        } else {
            return true;
        }
    }

    public StreamedContent exportPDF()
            throws DocumentException, MalformedURLException, IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PDFCreator pdfDoc;
        pdfDoc = new PDFCreator(0);
        PdfWriter.getInstance(pdfDoc.getDocument(), baos);
        pdfDoc.openDocument();
        pdfDoc.addMetaData();
        pdfDoc.addContent();
        pdfDoc.addQuestion("Termine: " + getEntryReport());
        pdfDoc.ontoResultTable(inferredRes);
        pdfDoc.addFooterPage();
        pdfDoc.closeDocument();
        ByteArrayInputStream stream = new ByteArrayInputStream(
                baos.toByteArray());
        return new DefaultStreamedContent(stream, "application/pdf", getEntryReport());
    }

    public StreamedContent exportExcel()
            throws DocumentException, MalformedURLException, IOException {

        // Creazione file excel: GIULIA COMPLETE IT !!!!
        Workbook wb = new HSSFWorkbook();
        Sheet termineSheet = wb.createSheet("termineSheet");
        Row headerRow = termineSheet.createRow(0);
        Cell termineHeaderCell = headerRow.createCell(0);
        termineHeaderCell.setCellValue("Termine");
        Cell propHeaderCell = headerRow.createCell(1);
        propHeaderCell.setCellValue("Proprietà");
        Cell valueHeaderCell = headerRow.createCell(2);
        valueHeaderCell.setCellValue("Valore");
        Cell inferredHeaderCell = headerRow.createCell(3);
        inferredHeaderCell.setCellValue("Inferita");

        int id = 1;
        for (OntoResult e : inferredRes) {
            Row dataRow = termineSheet.createRow(id);

            String termine = e.getTermine();
            Cell dataTermineCell = dataRow.createCell(0);
            dataTermineCell.setCellValue(termine);

            String prop = e.getRelazione();
            Cell dataPropCell = dataRow.createCell(1);
            dataPropCell.setCellValue(prop);

            String valore = e.getTermine_target();
            Cell dataValueCell = dataRow.createCell(2);
            dataValueCell.setCellValue(valore);

            String inferred = e.getInferita();
            Cell dataInferredCell = dataRow.createCell(3);
            dataInferredCell.setCellValue(inferred);
            id++;
        }
        // Fine creazione file excel

        // scrive excel nello stream output e ritorna il file excel esportato
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            wb.write(baos);
        } finally {
            baos.close();
        }
        ByteArrayInputStream stream = new ByteArrayInputStream(
                baos.toByteArray());
        return new DefaultStreamedContent(stream, "application/vnd.ms-excel", getEntryReport());
    }

    public OntoResult getSelectedTableRow() {
        return selectedTableRow;
    }

    public void setSelectedTableRow(OntoResult selectedTableRow) {
        this.selectedTableRow = selectedTableRow;
    }

    public void deleteRow() {
        inferredRes.remove(selectedTableRow);
        selectedTableRow = null;
    }

}

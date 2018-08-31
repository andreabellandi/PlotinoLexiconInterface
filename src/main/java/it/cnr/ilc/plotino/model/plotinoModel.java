/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package it.cnr.ilc.plotino.model;

import com.hp.hpl.jena.ontology.OntModel;
import com.hp.hpl.jena.ontology.OntModelSpec;
import com.hp.hpl.jena.rdf.model.ModelFactory;
import java.io.InputStream;
import java.io.Serializable;
import javax.annotation.PostConstruct;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Named;
import org.primefaces.event.FileUploadEvent;
import org.primefaces.model.UploadedFile;

@Named
@ApplicationScoped
public class plotinoModel implements Serializable {

    private static final long serialVersionUID = 2L;

    private static OntModel model = null;
    private static OntModel clearModel = null;

    @PostConstruct
    private void initOnto() {
        InputStream in = getClass().getClassLoader().getResourceAsStream("/Plotino27.owl");
        InputStream in2 = getClass().getClassLoader().getResourceAsStream("/Plotino27.owl");
        model = ModelFactory
                .createOntologyModel(OntModelSpec.OWL_MEM_MICRO_RULE_INF);
        model.read(in, "RDF/XML");
        clearModel = ModelFactory.createOntologyModel(OntModelSpec.OWL_MEM);
        clearModel.read(in2, "RDF/XML");
    }

    public OntModel getModel() {
        return model;
    }

    public OntModel getClearModel() {
        return clearModel;
    }

}

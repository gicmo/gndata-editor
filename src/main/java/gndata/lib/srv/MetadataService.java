// Copyright (c) 2014, German Neuroinformatics Node (G-Node)
//
// All rights reserved.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted under the terms of the BSD License. See
// LICENSE file in the root of the Project.

package gndata.lib.srv;

import java.io.IOException;
import java.nio.file.Path;

import com.hp.hpl.jena.ontology.*;
import com.hp.hpl.jena.query.*;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.reasoner.*;
import com.hp.hpl.jena.vocabulary.*;
import org.apache.jena.atlas.lib.StrUtils;
import org.apache.jena.riot.RDFDataMgr;

/**
 * Class implementing main functions working with project metadata
 */
public class MetadataService {

    private static final String stdPrefix = StrUtils.strjoinNL(
            "PREFIX rdf: <" + RDF.getURI() + ">",
            "PREFIX rdfs: <" + RDFS.getURI() + ">",
            "PREFIX owl: <" + OWL.getURI() + ">"
    );

    private OntModel schema;    // union model for all imported ontology files
    private Model annotations;  // model for data annotations

    public MetadataService(OntModel schema, Model annotations) {
        this.schema = schema;
        this.annotations = annotations;
    }

    /**
     * Returns a Ontology RDF Model instance to access default and
     * custom ontology terms.
     *
     * @return Ontology Model
     */
    public OntModel getSchema() {
        return schema;
    }

    /**
     * Returns an RDF Model instance to access annotations as stored RDF triples.
     *
     * @return RDF Model
     */
    public Model getAnnotations() {
        return annotations;
    }

    /**
     * Filters annotation literals by a given string.
     *
     * @return RDF Model with Subjects with matched literals and their RDF:types.
     */
    public Model getAnnotations(String literalFilter) {
        if (literalFilter.length() > 0) {
            String qs = StrUtils.strjoinNL(
                    "CONSTRUCT { ",
                    "?s rdf:type ?t .",
                    "?s ?p ?o",
                    "}",
                    "WHERE { ",
                    "?s rdf:type ?t .",
                    "?s ?p ?o . ",
                    "FILTER (",
                    "(STR(?p) != rdf:type) && ",
                    "isLiteral(?o) && ",
                    "regex(?o, '" + literalFilter + "', 'i')",
                    ")}"
            );

            return executeSPARQL(stdPrefix + "\n" + qs);
        } else {
            return getAnnotations();
        }
    }

    /**
     * Creates a new model with inferred relations based on loaded ontology,
     * annotations and reasoner.
     *
     * @return Model with inferred relations
     */
    public InfModel getAnnotationsWithInference() {
        return ModelFactory.createInfModel(getReasoner(), annotations);
    }

    /**
     * Creates a new reasoner based on actual schema and annotations.
     *
     * @return Reasoner
     */
    public Reasoner getReasoner() {
        Reasoner reasoner = ReasonerRegistry.getOWLReasoner();
        return reasoner.bindSchema(schema);
    }

    private Model executeSPARQL(String queryString) {
        Query query = QueryFactory.create(queryString);
        QueryExecution qexec = QueryExecutionFactory.create(query, getAnnotations());

        Model resultModel = qexec.execConstruct();
        qexec.close();

        return resultModel;
    }

    /**
     * Creates a new Metadata Service using a given path. Combines existing
     * project RDF schemas (ontology files) and metadata storage (annotations)
     * into a common Model. Creates default schemas if some do not exist.
     *
     * @return MetadataService
     */
    public static MetadataService create(String projectPath) throws IOException {
        if (projectPath == null) {
            throw new IOException("Cannot create metadata service at non-existing path");
        }

        MetadataFilesManager metaFiles = new MetadataFilesManager(projectPath);

        OntModel schema = ModelFactory.createOntologyModel(OntModelSpec.RDFS_MEM);

        // TODO figure out if using OntDocumentManager to read files makes sense
        for (Path p : metaFiles.schemaPaths()) {
            schema.addSubModel(RDFDataMgr.loadModel(p.toString()));
        }

        Model data = RDFDataMgr.loadModel(metaFiles.annotationsPath().toString());

        return new MetadataService(schema, data);
    }
}

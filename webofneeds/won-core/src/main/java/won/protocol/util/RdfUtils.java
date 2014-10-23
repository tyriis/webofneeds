package won.protocol.util;

import com.google.common.collect.Iterators;
import com.hp.hpl.jena.graph.Node;
import com.hp.hpl.jena.graph.Triple;
import com.hp.hpl.jena.query.Dataset;
import com.hp.hpl.jena.query.DatasetFactory;
import com.hp.hpl.jena.rdf.model.*;
import com.hp.hpl.jena.rdf.model.impl.StatementImpl;
import com.hp.hpl.jena.shared.Lock;
import com.hp.hpl.jena.sparql.path.Path;
import com.hp.hpl.jena.sparql.path.eval.PathEval;
import com.hp.hpl.jena.sparql.util.Context;
import com.hp.hpl.jena.util.FileUtils;
import com.hp.hpl.jena.util.ResourceUtils;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import won.protocol.exception.IncorrectPropertyCountException;
import won.protocol.model.Connection;
import won.protocol.model.ConnectionEvent;
import won.protocol.vocabulary.WON;

import java.io.*;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Utilities for RDF manipulation with Jena.
 */
public class RdfUtils
{
  public static final RDFNode EMPTY_RDF_NODE = null;


  private static final Logger logger = LoggerFactory.getLogger(RdfUtils.class);

  public static String toString(Model model)
  {
    String ret = "";

    if (model != null) {
      StringWriter sw = new StringWriter();
      model.write(sw, "TTL");
      ret = sw.toString();
    }

    return ret;
  }

  public static Model toModel(String content)
  {
    return readRdfSnippet(content, FileUtils.langTurtle);
  }

    /**
     * Converts a Jena Dataset into a TriG string
     *
     * @param dataset Dataset containing RDF which will be converted
     * @return <code>String</code> containing TriG serialized RDF from the dataset
     */
    public static String toString(Dataset dataset) {

        String result = "";

        if (dataset != null) {
            StringWriter sw = new StringWriter();
            RDFDataMgr.write(sw, dataset, RDFFormat.TRIG.getLang());
            result = sw.toString();
        }
        return result;
    }

    /**
     * Converts a <code>String</code> containing TriG formatted RDF into a Jena Dataset
     *
     * @param content String with the TriG formatted RDF
     * @return Jena Dataset containing the RDF from content
     */
    public static Dataset toDataset(String content) {
      return toDataset(content, RDFFormat.TRIG);
    }

  public static Dataset toDataset(String content, RDFFormat rdfFormat) {
    if (content != null) {
      return toDataset(new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8)), rdfFormat);
    } else
      return DatasetFactory.createMem();


  }

  public static Dataset toDataset(InputStream stream, RDFFormat rdfFormat) {

    Dataset dataset = DatasetFactory.createMem();

    RDFDataMgr.read(dataset, stream, rdfFormat.getLang());
    try {
      stream.close();
    } catch (IOException ex) {
      logger.warn("An exception occurred.", ex);
    }
    return dataset;
  }

    /**
   * Clones the specified model (its statements and ns prefixes) and returns the clone.
   * @param original
   * @return
   */
  public static Model cloneModel(Model original){
    Model clonedModel = ModelFactory.createDefaultModel();
    original.enterCriticalSection(Lock.READ);
    try {
      StmtIterator it = original.listStatements();
      while (it.hasNext()){
        clonedModel.add(it.nextStatement());
      }
      clonedModel.setNsPrefixes(original.getNsPrefixMap());
    } finally {
      original.leaveCriticalSection();
    }
    return clonedModel;
  }

  public static void replaceBaseURI(final Model model, final String baseURI)
  {
    //we assume that the RDF content is self-referential, i.e., it 'talks about itself': the graph is connected to
    //the public resource URI which, when de-referenced, returns that graph. So, triples referring to the 'null relative URI'
    //(see http://www.w3.org/2012/ldp/track/issues/20 ) will be changed to refer to the newly created need URI instead.
    //this implies that the default URI prefix of the document (if set) will have to be changed to the need URI.

    //check if there is a default URI prefix.
    //- If not, we just change the default prefix and that should automatically alter all
    //  null relative uris to refer to the newly set prefix.
    //- If there is one, fetch it as a resource and 'rename' it (i.e., replace all statements with exchanged name)
    if (model.getNsPrefixURI("") != null) {
      ResourceUtils.renameResource(
          model.getResource(model.getNsPrefixURI("")), baseURI
      );
    }
    //whatever the base uri (default URI prefix) was, set it to the need URI.
    model.setNsPrefix("", baseURI);
  }

  /**
   * Replaces the base URI that's set as the model's default URI prfefix in all statements by replacement.
   *
   * @param model
   * @param replacement
   */
  public static void replaceBaseResource(final Model model, final Resource replacement)
  {
    String baseURI = model.getNsPrefixURI("");
    if (baseURI == null) return;
    Resource baseUriResource = model.getResource(baseURI);
    replaceResourceInModel(baseUriResource, replacement);
    model.setNsPrefix("", replacement.getURI());
  }

  /**
   * Modifies the specified resources' model, replacing resource with replacement.
   * @param resource
   * @param replacement
   */
  private static void replaceResourceInModel(final Resource resource, final Resource replacement)
  {
    logger.debug("replacing resource '{}' with resource '{}'", resource, replacement);
    if (!resource.getModel().equals(replacement.getModel())) throw new IllegalArgumentException("resource and replacement must be from the same model");
    Model model = resource.getModel();
    Model modelForNewStatements = ModelFactory.createDefaultModel();
    StmtIterator iterator = model.listStatements(resource, (Property) null, (RDFNode) null);
    while (iterator.hasNext()) {
      Statement origStmt = iterator.next();
      Statement newStmt = new StatementImpl(replacement, origStmt.getPredicate(), origStmt.getObject());
      iterator.remove();
      modelForNewStatements.add(newStmt);
    }
    iterator = model.listStatements(null, (Property) null, (RDFNode) resource);
    while (iterator.hasNext()) {
      Statement origStmt = iterator.next();
      Statement newStmt = new StatementImpl(origStmt.getSubject(), origStmt.getPredicate(), replacement);
      iterator.remove();
      modelForNewStatements.add(newStmt);
    }
    model.add(modelForNewStatements);
  }

  /**
   * Creates a new model that contains both specified models' content. The base resource is that of model1,
   * all triples in model2 that are attached to the its base resource are modified so as to be attached to the
   * base resource of the result.
   * @param model1
   * @param model2
   * @return
   */
  public static Model mergeModelsCombiningBaseResource(final Model model1, final Model model2){
    if (logger.isDebugEnabled()){
      logger.debug("model1:\n{}",writeModelToString(model1, Lang.TTL));
      logger.debug("model2:\n{}",writeModelToString(model2, Lang.TTL));
    }
    Model result = ModelFactory.createDefaultModel();
    result.setNsPrefixes(mergeNsPrefixes(model1.getNsPrefixMap(), model2.getNsPrefixMap()));
    result.add(model1);
    result.add(model2);
    if (logger.isDebugEnabled()){
      logger.debug("result (before merging base resources):\n{}",writeModelToString(result, Lang.TTL));
    }
    Resource baseResource1 = getBaseResource(model1);
    Resource baseResource2 = getBaseResource(model2);
    replaceResourceInModel(result.getResource(baseResource1.getURI()), result.getResource(baseResource2.getURI()));
    result.setNsPrefix("",model1.getNsPrefixURI(""));
    if (logger.isDebugEnabled()){
      logger.debug("result (after merging base resources):\n{}",writeModelToString(result, Lang.TTL));
    }
    return result;
  }

  /**
   * Finds the resource representing the model's base resource, i.e. the resource with the
   * model's base URI. If no such URI is specified, a dummy base URI is set and a resource is
   * returned referencing that URI.
   *
   * @param model
   * @return
   */
  public static Resource findOrCreateBaseResource(Model model) {
    String baseURI = model.getNsPrefixURI("");
    if (baseURI == null){
      model.setNsPrefix("","no:uri");
      baseURI = model.getNsPrefixURI("");
    }
    return model.getResource(baseURI);
  }

  /**
   * Returns the resource representing the model's base resource, i.e. the resource with the
   * model's base URI.
   * @param model
   * @return
   */
  public static Resource getBaseResource(Model model){
    String baseURI = model.getNsPrefixURI("");
    if (baseURI == null) {
      return model.getResource("");
    } else {
      return model.getResource(baseURI);
    }
  }

  public static String writeModelToString(final Model model, final Lang lang)
  {
    StringWriter out = new StringWriter();
    RDFDataMgr.write(out, model, lang);
    return out.toString();
  }

  /**
   * Returns a copy of the specified resources' model where resource is replaced by replacement.
   * @param resource
   * @param replacement
   * @return
   */
  public static Model replaceResource(Resource resource, Resource replacement){
    if (!resource.getModel().equals(replacement.getModel())) throw new IllegalArgumentException("resource and replacement must be from the same model");
    Model result = ModelFactory.createDefaultModel();
    result.setNsPrefixes(resource.getModel().getNsPrefixMap());
    StmtIterator it = resource.getModel().listStatements();
    while (it.hasNext()){
      Statement stmt = it.nextStatement();
      Resource subject = stmt.getSubject();
      Resource predicate = stmt.getPredicate();
      RDFNode object = stmt.getObject();

      if (subject.equals(resource)){
        subject = replacement;
      }
      if (predicate.equals(resource)){
        predicate = replacement;
      }
      if (object.equals(resource)){
        object = replacement;
      }
      Triple triple = new Triple(subject.asNode(), predicate.asNode(), object.asNode());
      result.getGraph().add(triple);
    }
    return result;
  }

  /**
   * Adds the specified objectModel to the model of the specified subject. In the objectModel, the resource
   * that is identified by the objectModel's base URI (the "" URI prefix) will be replaced by a newly created
   * blank node(B, see later). All content of the objectModel is added to the model of the subject. An
   * additional triple (subject, property, B) is added. Moreover, the Namespace prefixes are merged.
   * @param subject
   * @param property
   * @param objectModel caution - will be modified
   */
  public static void attachModelByBaseResource(final Resource subject, final Property property, final Model objectModel)
  {
    attachModelByBaseResource(subject, property, objectModel, true);
  }

  /**
   * Adds the specified objectModel to the model of the specified subject. In the objectModel, the resource
   * that is identified by the objectModel's base URI (the "" URI prefix) will be replaced by a newly created
   * blank node(B, see later). All content of the objectModel is added to the model of the subject. An
   * additional triple (subject, property, B) is added. Moreover, the Namespace prefixes are merged.
   * @param subject
   * @param property
   * @param objectModel caution - will be modified
   * @param replaceBaseResourceByBlankNode
   */
  public static void attachModelByBaseResource(final Resource subject, final Property property, final Model objectModel, final boolean replaceBaseResourceByBlankNode){
    Model subjectModel = subject.getModel();
    //either explicitly use blank node, or do so if there is no base resource prefix
    //as the model may have triples containing the null relative URI.
    // we still want to attach these and try to get them by using
    //the empty URI, and replacing that resource by a blank node
    if (replaceBaseResourceByBlankNode || objectModel.getNsPrefixURI("") == null){
      //create temporary resource and replace objectModel's base resource to avoid clashes
      String tempURI = "tmp:"+Integer.toHexString(objectModel.hashCode());
      replaceBaseResource(objectModel, objectModel.createResource(tempURI));
      //merge models
      subjectModel.add(objectModel);
      //replace temporary resource by blank node
      Resource blankNode = subjectModel.createResource();
      subject.addProperty(property, blankNode);
      replaceResourceInModel(subjectModel.getResource(tempURI), blankNode);
      //merge the prefixes, but don't add the objectModel's default prefix in any case - we don't want it to end up as
      //the resulting model's base prefix.
      Map<String, String> objectModelPrefixes = objectModel.getNsPrefixMap();
      objectModelPrefixes.remove("");
      subjectModel.setNsPrefixes(mergeNsPrefixes(subjectModel.getNsPrefixMap(), objectModelPrefixes));
    } else {
      String baseURI = objectModel.getNsPrefixURI("");
      Resource baseResource = objectModel.getResource(baseURI); //getResource because it may exist already
      subjectModel.add(objectModel);
      baseResource = subjectModel.getResource(baseResource.getURI());
      subject.addProperty(property, baseResource);
      RdfUtils.replaceBaseResource(subjectModel, baseResource);
    }
  }

  /**
   * Creates a new Map object containing all prefixes from both specified maps. When prefix mappings clash, the mappings
   * from prioritaryPrefixes are used.
   * @param prioritaryPrefixes
   * @param additionalPrefixes
   * @return
   */
  public static Map<String, String> mergeNsPrefixes(final Map<String, String> prioritaryPrefixes, final Map<String, String> additionalPrefixes)
  {
    Map<String, String> mergedPrefixes = new HashMap<String, String>();
    mergedPrefixes.putAll(additionalPrefixes);
    mergedPrefixes.putAll(prioritaryPrefixes); //overwrites the additional prefixes when clashing
    return mergedPrefixes;
  }

  /**
   * Reads the InputStream into a Model. Sets a 'fantasy URI' as base URI and handles it gracefully if
   * the model read from the string defines its own base URI. Special care is taken that the null relative URI ('<>')
   * is replaced by the baseURI.
   * @param in
   * @param rdfLanguage
   * @return a Model (possibly empty)
   */
  public static Model readRdfSnippet(final InputStream in, final String rdfLanguage)
  {
    com.hp.hpl.jena.rdf.model.Model model = ModelFactory.createDefaultModel();
    if (in == null) return model;
    String baseURI= "no:uri";
    model.setNsPrefix("", baseURI);
    model.read(in, baseURI, rdfLanguage);
    String baseURIAfterReading = model.getNsPrefixURI("");
    if (baseURIAfterReading == null){
      model.setNsPrefix("",baseURI);
    } else if (!baseURI.equals(baseURIAfterReading)){
      //the string representation of the model specified a base URI, but we used a different one for reading.
      //We need to make sure that the one that is now set is the only one used
      ResourceUtils.renameResource(model.getResource(baseURI), model.getNsPrefixURI(""));
    }
    return model;
  }

  /**
   * Reads the InputStream into a Model. Sets a 'fantasy URI' as base URI and handles it gracefully if
   * the model read from the string defines its own base URI. Special care is taken that the null relative URI ('<>')
   * is replaced by the baseURI.
   * @param in
   * @param rdfLanguage
   * @return a Model (possibly empty)
   */
  public static Model readRdfSnippet(final Reader in, final String rdfLanguage)
  {
    com.hp.hpl.jena.rdf.model.Model model = ModelFactory.createDefaultModel();
    if (in == null) return model;
    String baseURI= "no:uri";
    model.setNsPrefix("", baseURI);
    model.read(in, baseURI, rdfLanguage);
    String baseURIAfterReading = model.getNsPrefixURI("");
    if (baseURIAfterReading == null){
      model.setNsPrefix("",baseURI);
    } else if (!baseURI.equals(baseURIAfterReading)){
      //the string representation of the model specified a base URI, but we used a different one for reading.
      //We need to make sure that the one that is now set is the only one used
      ResourceUtils.renameResource(model.getResource(baseURI), model.getNsPrefixURI(""));
    }
    return model;
  }

  /**
   * Reads the specified string into a Model. Sets a 'fantasy URI' as base URI and handles it gracefully if
   * the model read from the string defines its own base URI. Special care is taken that the null relative URI ('<>')
   * is replaced by the baseURI.
   * @param rdfAsString
   * @param rdfLanguage
   * @return a Model (possibly empty)
   */
  public static Model readRdfSnippet(final String rdfAsString, final String rdfLanguage)
  {
    return readRdfSnippet(new StringReader(rdfAsString), rdfLanguage);
  }


  /**
   * Evaluates the path on the model obtained by dereferencing the specified resourceURI.
   * If the path resolves to multiple resources, only the first one is returned.
   * <br />
   * <br />
   * Note: For more information on property paths, see http://jena.sourceforge.net/ARQ/property_paths.html
   * <br />
   * To create a Path object for the path "rdf:type/rdfs:subClassOf*":
   * <pre>
   * Path path = PathParser.parse("rdf:type/rdfs:subClassOf*", PrefixMapping.Standard) ;
   * </pre>
   * @param resourceURI
   * @param propertyPath
   * @return null if the model is empty or the path does not resolve to a node
   * @throws  IllegalArgumentException if the node found by the path is not a URI
   */
  public static URI getURIPropertyForPropertyPath(final Model model, final URI resourceURI, Path propertyPath)
  {
    return toURI(getNodeForPropertyPath(model, resourceURI, propertyPath));
  }

  /**
   * Evaluates the path on all models in the dataset obtained by dereferencing the specified resourceURI.
   * If the path resolves to multiple resources, only the first one is returned.
   * <br />
   * <br />
   * Note: For more information on property paths, see http://jena.sourceforge.net/ARQ/property_paths.html
   * <br />
   * To create a Path object for the path "rdf:type/rdfs:subClassOf*":
   * <pre>
   * Path path = PathParser.parse("rdf:type/rdfs:subClassOf*", PrefixMapping.Standard) ;
   * </pre>
   * @param resourceURI
   * @param propertyPath
   * @return null if the model is empty or the path does not resolve to a node
   * @throws  IllegalArgumentException if the node found by the path is not a URI
   */
  public static URI getURIPropertyForPropertyPath(final Dataset dataset, final URI resourceURI, Path propertyPath)
  {
    return toURI(getNodeForPropertyPath(dataset, resourceURI, propertyPath));
  }

  public static Iterator<URI> getURIsForPropertyPath(final Model model, final URI resourceURI, Path propertyPath)
  {
    Iterator<Node> nodeIterator = getNodesForPropertyPath(model,resourceURI,propertyPath);
    return new ProjectingIterator<Node, URI>(nodeIterator) {
        @Override
        public URI next() {
          return toURI(this.baseIterator.next());
        }
      };
  }

  public static Iterator<URI> getURIsForPropertyPath(final Dataset dataset, final URI resourceURI, Path propertyPath)
  {
    Iterator<Node> nodeIterator = getNodesForPropertyPath(dataset,resourceURI,propertyPath);
    return new ProjectingIterator<Node, URI>(nodeIterator) {
      @Override
      public URI next() {
        return toURI(this.baseIterator.next());
      }
    };
  }



  /**
   * Evaluates the path on the model obtained by dereferencing the specified resourceURI.
   * If the path resolves to multiple resources, only the first one is returned.
   * <br />
   * <br />
   * Note: For more information on property paths, see http://jena.sourceforge.net/ARQ/property_paths.html
   * <br />
   * To create a Path object for the path "rdf:type/rdfs:subClassOf*":
   * <pre>
   * Path path = PathParser.parse("rdf:type/rdfs:subClassOf*", PrefixMapping.Standard) ;
   * </pre>
   * @param resourceURI
   * @param propertyPath
   * @return null if the model is empty or the path does not resolve to a node
   */
  public static String getStringPropertyForPropertyPath(final Model model, final URI resourceURI, Path propertyPath)
  {
    return toString(getNodeForPropertyPath(model, resourceURI, propertyPath));
  }

  /**
   * Evaluates the path on each model in the dataset obtained by dereferencing the specified resourceURI.
   * If the path resolves to multiple resources, only the first one is returned.
   * <br />
   * <br />
   * Note: For more information on property paths, see http://jena.sourceforge.net/ARQ/property_paths.html
   * <br />
   * To create a Path object for the path "rdf:type/rdfs:subClassOf*":
   * <pre>
   * Path path = PathParser.parse("rdf:type/rdfs:subClassOf*", PrefixMapping.Standard) ;
   * </pre>
   * @param resourceURI
   * @param propertyPath
   * @return null if the model is empty or the path does not resolve to a node
   */
  public static String getStringPropertyForPropertyPath(final Dataset dataset, final URI resourceURI, Path propertyPath)
  {
    return toString(getNodeForPropertyPath(dataset, resourceURI, propertyPath));
  }

  /**
   * Returns the literal lexical form of the specified node or null if the node is null.
   * @param node
   * @return
   */
  public static String toString(Node node){
    if (node == null) return null;
    return node.getLiteralLexicalForm();
  }

  /**
   * Returns the URI of the specified node or null if the node is null. If the node does not
   * represent a resource, an UnsupportedOperationException is thrown.
   * @param node
   * @return
   */
  public static URI toURI(Node node){
    if (node == null) return null;
    return URI.create(node.getURI());
  }


  /**
   * Returns the first RDF node found in the specified model for the specified property path.
   * @param model
   * @param resourceURI
   * @param propertyPath
   * @return
   */
  public static Node getNodeForPropertyPath(final Model model, URI resourceURI, Path propertyPath) {
    //Iterator<Node> result =  PathEval.eval(model.getGraph(), model.getResource(resourceURI.toString()).asNode(),
    //                                        propertyPath);
    Iterator<Node> result =  PathEval.eval(model.getGraph(), model.getResource(resourceURI.toString()).asNode(),
                                           propertyPath, Context.emptyContext);

    if (!result.hasNext()) return null;
    return result.next();
  }

  /**
   * Returns the first RDF node found in the specified dataset for the specified property path.
   * @param dataset
   * @param resourceURI
   * @param propertyPath
   * @return
   */
  public static Node getNodeForPropertyPath(final Dataset dataset, final URI resourceURI, final Path propertyPath) {
    return findFirst(dataset, new ModelVisitor<Node>()
    {
      @Override
      public Node visit(final Model model) {
        return getNodeForPropertyPath(model, resourceURI, propertyPath);
      }
    });
  }

  /**
   * Evaluates the specified path in the specified model, starting with the specified resourceURI.
   * @param model
   * @param resourceURI
   * @param propertyPath
   * @return
   */
  public static Iterator<Node> getNodesForPropertyPath(final Model model, URI resourceURI, Path propertyPath) {
    Iterator<Node> result =  PathEval.eval(model.getGraph(), model.getResource(resourceURI.toString()).asNode(),
                                           propertyPath, Context.emptyContext);
    return result;
  }


  /**
   * Evaluates the specified path in each model of the specified dataset, starting with the specified resourceURI.
   * @param dataset
   * @param resourceURI
   * @param propertyPath
   * @return
   */
  public static Iterator<Node> getNodesForPropertyPath(final Dataset dataset, final URI resourceURI, final Path propertyPath) {
    return Iterators.concat(
      visit(dataset, new ModelVisitor<Iterator<Node>>()
      {
        @Override
        public Iterator<Node> visit(final Model model) {
          return getNodesForPropertyPath(model, resourceURI, propertyPath);
        }
      })
    );
  }



  /**
   * Dataset visitor used for repeated application of model operations in a dataset.
   */
  public static interface ModelVisitor<T> {
    public T visit(Model model);
  }

  /**
   * ModelSelector used to select which models in a dataset to visit.
   */
  public static interface ModelSelector {
    public Iterator<Model> select(Dataset dataset);
  }

  /**
   * ResultCombiner which combines to results of type T and returns it.
   */
  public static interface ResultCombiner<T> {
    public T combine(T first, T second);
  }

  /**
   * Selector that selects all models, including the default model.
   * The first model is the default model, the named models are returned in the order
   * specified by Dataset.listNames().
   */
  public static class DefaultModelSelector implements ModelSelector{
    @Override
    public Iterator<Model> select(final Dataset dataset) {
      List ret = new LinkedList<Model>();
      Model model = dataset.getDefaultModel();
      if (model != null) {
        ret.add(model);
      }
      for (Iterator<String> modelNames = dataset.listNames(); modelNames.hasNext(); ){
        ret.add(dataset.getNamedModel(modelNames.next()));
      }
      return ret.iterator();
    }
  }

  private static ModelSelector DEFAULT_MODEL_SELECTOR = new DefaultModelSelector();

  /**
   * Returns a thread-safe, shared default model selector.
   * @return
   */
  public static ModelSelector getDefaultModelSelector(){
    return DEFAULT_MODEL_SELECTOR;
  };

  /**
   * Calls the specified ModelVisitor's visit method on each model of the dataset that is selected by the ModelSelector.
   * The rsults are collected in the returned iterator.
   * @param dataset
   * @param visitor
   * @param modelSelector
   * @param <T>
   * @return
   */
  public static <T> Iterator<T> visit(Dataset dataset, ModelVisitor<T> visitor, ModelSelector modelSelector){
    List<T> results = new LinkedList<T>();
    for (Iterator<Model> modelIterator = modelSelector.select(dataset); modelIterator.hasNext();){
      results.add(visitor.visit(modelIterator.next()));
    }
    return results.iterator();
  }

  public static <T> Iterator<T> visit(Dataset dataset, ModelVisitor<T> visitor){
    return visit(dataset, visitor, getDefaultModelSelector());
  }

  /**
   * Visits all models and flattens the collections returned by the visitor into one list.
   * @param dataset
   * @param visitor
   * @param modelSelector
   * @param <T>
   * @return
   */
  public static <E, T extends Collection<E>> List<E> visitFlattenedToList(Dataset dataset, ModelVisitor<T> visitor,
    ModelSelector modelSelector){
    List<E> results = new ArrayList<E>();
    for (Iterator<Model> modelIterator = modelSelector.select(dataset); modelIterator.hasNext();){
      results.addAll(visitor.visit(modelIterator.next()));
    }
    return results;
  }

  public static <E, T extends Collection<E>> List<E> visitFlattenedToList(Dataset dataset, ModelVisitor<T> visitor) {
    return visitFlattenedToList(dataset, visitor, getDefaultModelSelector());
  }

  /**
   * Visits all models and flattens the NodeIterator returned by the visitor into one.
   * Returns null if all visitors return null.
   * @param dataset
   * @param visitor
   * @param modelSelector
   * @return
   */
  public static NodeIterator visitFlattenedToNodeIterator(Dataset dataset,
    ModelVisitor<NodeIterator> visitor,
    ModelSelector modelSelector){
    NodeIterator it = null;
    for (Iterator<Model> modelIterator = modelSelector.select(dataset); modelIterator.hasNext();){
      NodeIterator currentIt = visitor.visit(modelIterator.next());
      if (it == null) {
        it = currentIt;
      } else {
        it.andThen(currentIt);
      }
    }
    return it;
  }


  public static NodeIterator visitFlattenedToNodeIterator(Dataset dataset,
    ModelVisitor<NodeIterator> visitor){
    return visitFlattenedToNodeIterator(dataset, visitor, getDefaultModelSelector());
  }

  /**
   * Returns the first non-null result obtained by calling the specified ModelVisitor's visit method in the order
   * defined by the specified ModelSelector.
   * @param dataset
   * @param visitor
   * @param modelSelector
   * @param <T>
   * @return
   */
  public static <T> T findFirst(Dataset dataset, ModelVisitor<T> visitor, ModelSelector modelSelector){
    List<T> results = new LinkedList<T>();
    for (Iterator<Model> modelIterator = modelSelector.select(dataset); modelIterator.hasNext();){
      T result = visitor.visit(modelIterator.next());
      if (result != null) return result;
    }
    return null;
  }

  public static <T> T findFirst(Dataset dataset, ModelVisitor<T> visitor){
    return findFirst(dataset, visitor, getDefaultModelSelector());
  }

  /**
   * Returns the result obtained by calling the specified ModelVisitor's visit method in the order
   * defined by the specified ModelSelector. Throws an IncorrectPropertyCountException if no result or
   * more than one result is found. ModelVisitors should implement the same exception strategy.
   * @param dataset
   * @param visitor
   * @param modelSelector
   * @param allowSame if true, multiple results will be checked with equals() and if they are are equal, no
   *                  exception is thrown
   * @param <T>
   * @return
   */
  public static <T> T findOne(Dataset dataset, ModelVisitor<T> visitor, ModelSelector modelSelector, boolean allowSame){
    T result = null;
    for (Iterator<Model> modelIterator = modelSelector.select(dataset); modelIterator.hasNext();) {
      T newResult = visitor.visit(modelIterator.next());
      if (newResult != null) {
        if (result != null) {
          if (!allowSame || !result.equals(newResult)) {
            throw new IncorrectPropertyCountException("Results were found in more than " +
                                                        "one model", 1, 2);
          }
        }
        result = newResult;
      }
    }
    if (result == null)
      throw new IncorrectPropertyCountException("No result found", 1, 0);
    return result;
  }

  public static <T> T findOne(Dataset dataset, ModelVisitor<T> visitor, boolean allowSame){
    return findOne(dataset, visitor, getDefaultModelSelector(), allowSame);
  }

  /**
   * Returns the result obtained by calling the specified ModelVisitor's visit method where the
   * result is combined with the ResultCombiner's combine method.
   * @param dataset
   * @param visitor
   * @param modelSelector
   * @param resultCombiner
   * @param <T>
   * @return
   */
  public static <T> T applyMethod(Dataset dataset, ModelVisitor<T> visitor,
                                  ModelSelector modelSelector,
                                  ResultCombiner<T> resultCombiner) {
    T result = null;
    for (Iterator<Model> modelIterator = modelSelector.select(dataset); modelIterator.hasNext();) {
      T newResult = visitor.visit(modelIterator.next());
      if (result != null)
        result = resultCombiner.combine(result, newResult);
      else
        result = newResult;
    }
    return result;
  }

  public static <T> T applyMethod(Dataset dataset,
                                  ModelVisitor<T> visitor,
                                  ResultCombiner<T> resultCombiner) {
    return applyMethod(dataset, visitor, getDefaultModelSelector(), resultCombiner);
  }

  /**
   * Finds resource which is a specified property of a specified resource.
   * If multiple (non equal) resources are found an exception is thrown.
   *
   *
   * @param dataset <code>Dataset</code> to look into
   * @param resourceURI
   * @param p
   * @return <code>URI</code> of the resource
   */
  public static RDFNode findOnePropertyFromResource(Dataset dataset, final URI resourceURI, final Property p) {
    return RdfUtils.findOne(dataset, new RdfUtils.ModelVisitor<RDFNode>()
    {
      @Override
      public RDFNode visit(final Model model) {
        return findOnePropertyFromResource(model, resourceURI, p);
      }
    }, true);
  }
  /**
   * Finds resource which is a specified property of a specified resource.
   * If multiple (non equal) resources are found an exception is thrown.
   *
   *
   * @param model <code>Model</code> to look into
   * @param resourceURI
   * @param property
   * @return <code>URI</code> of the resource
   */
  public static RDFNode findOnePropertyFromResource(Model model, URI resourceURI, Property property) {

    List<RDFNode> foundNodes = new ArrayList<RDFNode>();

    NodeIterator iterator = model.listObjectsOfProperty(model.createResource(resourceURI.toString()), property);
    while (iterator.hasNext()) {
      foundNodes.add(iterator.next());
    }
    if (foundNodes.size() == 0)
      return null;
    else if (foundNodes.size() == 1)
      return foundNodes.get(0);
    else if (foundNodes.size() > 1) {
      RDFNode n = foundNodes.get(0);
      for (RDFNode node : foundNodes) {
        if (!node.equals(n))
          throw new IncorrectPropertyCountException(1,2);
      }
      return n;
    }
    else
      return null;
  }

  /**
   * Finds the first triple in the specified model that has the specified propery and object.
   * The subject is expected to be a resource.
   * @param model
   * @param property
   * @param object
   * @param allowMultiple if false, will throw an IllegalArgumentException if more than one triple is found
   * @param allowNone if false, will throw an IllegalArgumentException if no triple is found
   * @return
   */
  public static URI findFirstObjectUri(Model model, Property property, RDFNode object, boolean allowMultiple,
    boolean allowNone){
    URI retVal = null;
    StmtIterator it = model.listStatements(null, property, object);
    if (!it.hasNext() && !allowNone) throw new IllegalArgumentException("expecting at least one triple");
    if (it.hasNext()){
      retVal = URI.create(it.nextStatement().getSubject().asResource().toString());
    }
    if (!allowMultiple && it.hasNext()) throw new IllegalArgumentException("not expecting more than one triple");
    return retVal;
  }

  /**
   * Creates a new graph URI for the specified dataset by appending
   * a specified string (toAppend) and then n alphanumeric characters to the
   * specified String.
   * It is guaranteed that the resulting URI is not used as a graph
   * name in the specified dataset.
   *
   * Note that the implementation is not synchronized, so concurrent
   * executions of the method may result in identical URIs being returned.
   *
   * If both the specified baseURI and the toAppend string contain a hash sign ('#'),
   * the hash-part will be removed from the base uri before the result will be crated.
   *
   * @param baseURI the URI to be extended.
   * @param toAppend a string that will be appended directly to the URI.
   * @param length number of alphanumeric characters that are appended to <code>toAppend</code>.
   * @param dataset the dataset that will be checked to determine if the resulting URI is new.
   * @return an URI that is previously unused as a graph URI.
   */
   public static URI createNewGraphURI(String baseURI, String toAppend, int length, Dataset dataset){
     if (toAppend.contains("#")){
       int hashIndex = baseURI.indexOf('#');
       if (hashIndex > -1){
         baseURI = baseURI.substring(0,hashIndex);
       }
     }
     CheapInsecureRandomString randomString = new CheapInsecureRandomString(length);
     int maxTries = 5;
     for (int i = 0; i < maxTries; i++){
       String graphName = baseURI + toAppend + randomString.nextString();
       if (!dataset.containsNamedModel(graphName)){
         return URI.create(graphName);
       }
     } ;
     throw new IllegalStateException("Tried " + maxTries +" times to generate a new graph URI (" + length + " random" +
       " characters), but were unable to generate a previously unused one; giving up.");
   }

  /**
   * Stores additional data if there is any in the specified model.
   * TODO: Move to WonRdfUtils
   *
   * @param eventURI
   * @param content
   * @param con
   * @param event
   * @param score
   */
  public static Model createContentForEvent(final URI eventURI, final Model content, final Connection con,
                                            final ConnectionEvent event, final Double score) {
    //TODO: define what content may contain and check that here! May content contain any RDF or must it be linked to the <> node?
    Model extraDataModel = ModelFactory.createDefaultModel();
    Resource eventNode = extraDataModel.createResource(eventURI.toString());
    if(score != null)
      eventNode.addLiteral(WON.HAS_MATCH_SCORE, score.doubleValue());
    extraDataModel.setNsPrefix("", eventNode.getURI().toString());
    if (content != null) {

      //TODO: check if the correct data is saved
      extraDataModel.add(content);
      RdfUtils.replaceBaseResource(extraDataModel, eventNode);
    }
    return extraDataModel;
  }


  public static void addAllStatements(Model toModel, Model fromModel) {
    StmtIterator stmtIterator = fromModel.listStatements();
    while (stmtIterator.hasNext()) {
      toModel.add(stmtIterator.nextStatement());
    }
  }

  public static void addPrefixMapping(Model toModel, Model fromModel) {
    for (String prefix : fromModel.getNsPrefixMap().keySet()) {
      String uri = toModel.getNsPrefixURI(prefix);
      if (uri == null) { // if no such prefix-uri yet, add it
        toModel.setNsPrefix(prefix, fromModel.getNsPrefixURI(prefix));
      } else {
        if (uri.equals(fromModel.getNsPrefixURI(prefix))) {
          // prefix-uri is already there, do nothing
        } else {
          // prefix-uri collision, redefine prefix
          int counter = 2;
          while (!toModel.getNsPrefixMap().containsKey(prefix + counter)) {
            counter++;
          }
          toModel.setNsPrefix(prefix + counter, fromModel.getNsPrefixURI(prefix));
        }
      }
    }
  }


  public static List<String> getModelNames(Dataset dataset) {
    List<String> modelNames = new ArrayList<String>();
    Iterator<String> names = dataset.listNames();
    while (names.hasNext()) {
      modelNames.add(names.next());
    }
    return modelNames;
  }


  public static String writeDatasetToString(final Dataset dataset, final Lang lang)
  {
    StringWriter sw = new StringWriter();
    RDFDataMgr.write(sw, dataset, lang);
    return sw.toString();
  }

  public static Dataset readDatasetFromString(final String data, final Lang lang)
  {
    StringReader sr = new StringReader(data);
    Dataset dataset = DatasetFactory.createMem();
    RDFDataMgr.read(dataset, sr, "no:uri", lang);
    return dataset;
  }

  /**
   * Adds the second dataset to the first one, merging default models and models with identical name.
   * @param baseDataset
   * @param toBeAddedtoBase
   */
  public static void addDatasetToDataset(final Dataset baseDataset, final Dataset toBeAddedtoBase) {
    assert baseDataset != null : "baseDataset must not be null";
    assert toBeAddedtoBase != null : "toBeAddedToBase must not be null";
    baseDataset.getDefaultModel().add(toBeAddedtoBase.getDefaultModel());
    for ( Iterator<String> nameIt = toBeAddedtoBase.listNames(); nameIt.hasNext();){
      String modelName = nameIt.next();
      if (baseDataset.containsNamedModel(modelName)) {
        baseDataset.getNamedModel(modelName).add(toBeAddedtoBase.getNamedModel(modelName));
      } else {
        baseDataset.addNamedModel(modelName, toBeAddedtoBase.getNamedModel(modelName));
      }
    }
  }

  /**
   * Adds all triples of the dataset to the model.
   * @param dataset
   * @param model
   */
  public static void copyDatasetTriplesToModel(final Dataset dataset, final Model model) {
    assert dataset != null : "dataset must not be null";
    assert model != null : "model must not be null";
    visit(dataset, new ModelVisitor<Object>()
    {
      @Override
      public Object visit(final Model datasetModel) {
        model.add(datasetModel);
        return null;
      }
    });
  }
}

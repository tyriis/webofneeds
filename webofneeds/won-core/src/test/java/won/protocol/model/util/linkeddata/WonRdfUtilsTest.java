package won.protocol.model.util.linkeddata;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.LoggerFactory;
import won.protocol.util.WonRdfUtils;
import won.protocol.vocabulary.WXCHAT;
import won.protocol.vocabulary.WXGROUP;

import java.io.InputStream;
import java.net.URI;
import java.util.Collection;

public class WonRdfUtilsTest {
    private InputStream getResourceAsStream(String name) {
        return getClass().getClassLoader().getResourceAsStream(name);
    }

    private Dataset loadTestDatasetFromClasspathResource(String resource) {
        Dataset dataset = DatasetFactory.createGeneral();
        RDFDataMgr.read(dataset, getResourceAsStream(resource), Lang.TRIG);
        return dataset;
    }

    @BeforeClass
    public static void setLogLevel() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.setLevel(Level.INFO);
    }

    @Test
    public void testGetAtomURI() {
        Dataset atomDataset = loadTestDatasetFromClasspathResource("wonrdfutils/atom1.trig");
        URI atomURI = WonRdfUtils.AtomUtils.getAtomURI(atomDataset);
        Assert.assertEquals(URI.create("https://192.168.124.49:8443/won/resource/atom/cbfgi37je6kr"), atomURI);
    }

    @Test
    public void testGetSocketsOfTypeOneSocket() {
        Dataset atomDataset = loadTestDatasetFromClasspathResource("wonrdfutils/atom1.trig");
        URI atomURI = WonRdfUtils.AtomUtils.getAtomURI(atomDataset);
        Collection<URI> sockets = WonRdfUtils.SocketUtils.getSocketsOfType(atomDataset, atomURI,
                        URI.create(WXCHAT.ChatSocketString));
        Assert.assertEquals(1, sockets.size());
        Assert.assertEquals(URI.create("https://192.168.124.49:8443/won/resource/atom/cbfgi37je6kr#chatSocket"),
                        sockets.stream().findFirst().get());
    }

    @Test
    public void testGetSocketsOfTypeTwoSockets() {
        Dataset atomDataset = loadTestDatasetFromClasspathResource("wonrdfutils/atom1.trig");
        URI atomURI = WonRdfUtils.AtomUtils.getAtomURI(atomDataset);
        Collection<URI> sockets = WonRdfUtils.SocketUtils.getSocketsOfType(atomDataset, atomURI,
                        URI.create(WXGROUP.GroupSocketString));
        Assert.assertEquals(2, sockets.size());
        Assert.assertTrue(sockets.contains(
                        URI.create("https://192.168.124.49:8443/won/resource/atom/cbfgi37je6kr#groupSocket1")));
        Assert.assertTrue(sockets.contains(
                        URI.create("https://192.168.124.49:8443/won/resource/atom/cbfgi37je6kr#groupSocket1")));
    }
}

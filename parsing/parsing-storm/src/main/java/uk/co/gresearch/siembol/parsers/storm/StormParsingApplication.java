package uk.co.gresearch.siembol.parsers.storm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.StormTopology;
import org.apache.storm.kafka.spout.KafkaSpout;
import org.apache.storm.kafka.spout.KafkaSpoutConfig;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.Fields;
import org.apache.storm.tuple.Values;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import uk.co.gresearch.siembol.common.storm.KafkaBatchWriterBolt;
import uk.co.gresearch.siembol.common.storm.StormAttributes;
import uk.co.gresearch.siembol.common.storm.StormHelper;
import uk.co.gresearch.siembol.common.zookeper.ZookeperAttributes;
import uk.co.gresearch.siembol.common.zookeper.ZookeperConnector;
import uk.co.gresearch.siembol.common.zookeper.ZookeperConnectorFactory;
import uk.co.gresearch.siembol.parsers.application.factory.ParsingApplicationFactoryAttributes;
import uk.co.gresearch.siembol.parsers.application.factory.ParsingApplicationFactoryImpl;
import uk.co.gresearch.siembol.parsers.application.factory.ParsingApplicationFactoryResult;

import java.lang.invoke.MethodHandles;
import java.util.Base64;

import static org.apache.kafka.clients.consumer.ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.GROUP_ID_CONFIG;
import static org.apache.kafka.clients.consumer.ConsumerConfig.CLIENT_ID_CONFIG;

public class StormParsingApplication {
    private static final Logger LOG =
            LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    private static final String KAFKA_SPOUT = "kafka-spout";
    private static final String KAFKA_WRITER = "kafka-writer";
    private static final int EXPECTED_ARG_SIZE = 2;
    private static final int STORM_ATTR_INDEX = 0;
    private static final int PARSING_ATTR_INDEX = 1;
    private static final String WRONG_ARGUMENT_MSG =  "Wrong arguments. The application expects " +
            "Base64 encoded storm attributes and parsing app attributes";
    private static final String KAFKA_PRINCIPAL_FORMAT_MSG = "%s.%s";
    private static final String TOPOLOGY_NAME_FORMAT_MSG = "parsing-%s";
    private static final String SUBMIT_INFO_LOG = "Submitted parsing application storm topology: {} " +
            "with storm attributes: {}\nparsing application attributes: {}";

    private static KafkaSpoutConfig<String, byte[]> createKafkaSpoutConfig(
            StormParsingApplicationAttributes parsingApplicationAttributes) {
        StormAttributes stormAttributes = parsingApplicationAttributes.getStormAttributes();
        stormAttributes.getKafkaSpoutProperties().put(KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        stormAttributes.getKafkaSpoutProperties().put(VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class);

        return StormHelper.createKafkaSpoutConfig(stormAttributes,
                r -> new Values(r.key(), r.value()),
                new Fields(ParsingApplicationTuples.METADATA.toString(), ParsingApplicationTuples.LOG.toString()));
    }

    public static StormTopology createTopology(StormParsingApplicationAttributes stormAppAttributes,
                                               ParsingApplicationFactoryAttributes parsingAttributes,
                                               ZookeperConnectorFactory zookeperConnectorFactory) throws Exception {
        stormAppAttributes.getStormAttributes().getKafkaSpoutProperties()
                .put(GROUP_ID_CONFIG, String.format(KAFKA_PRINCIPAL_FORMAT_MSG,
                        stormAppAttributes.getGroupIdPrefix(), parsingAttributes.getName()));

        stormAppAttributes.getKafkaBatchWriterAttributes().getProducerProperties().
                put(CLIENT_ID_CONFIG, String.format(KAFKA_PRINCIPAL_FORMAT_MSG,
                        stormAppAttributes.getClientIdPrefix(), parsingAttributes.getName()));
        stormAppAttributes.getStormAttributes().setKafkaTopics(parsingAttributes.getInputTopics());

        TopologyBuilder builder = new TopologyBuilder();
        builder.setSpout(KAFKA_SPOUT,
                new KafkaSpout<>(createKafkaSpoutConfig(stormAppAttributes)),
                parsingAttributes.getInputParallelism());

        builder.setBolt(parsingAttributes.getName(),
                new ParsingApplicationBolt(stormAppAttributes, parsingAttributes, zookeperConnectorFactory),
                parsingAttributes.getParsingParallelism())
                .localOrShuffleGrouping(KAFKA_SPOUT);

        builder.setBolt(KAFKA_WRITER,
                new KafkaBatchWriterBolt(stormAppAttributes.getKafkaBatchWriterAttributes(),
                        ParsingApplicationTuples.PARSING_MESSAGES.toString()),
                parsingAttributes.getOutputParallelism())
                .localOrShuffleGrouping(parsingAttributes.getName());

        return builder.createTopology();
    }

    public static void main(String[] args) throws Exception {
        if(args.length != EXPECTED_ARG_SIZE) {
            LOG.error(WRONG_ARGUMENT_MSG);
            throw new IllegalArgumentException(WRONG_ARGUMENT_MSG);
        }

        String stormAttributesStr = new String(Base64.getDecoder().decode(args[STORM_ATTR_INDEX]));
        String parsingAttributesStr = new String(Base64.getDecoder().decode(args[PARSING_ATTR_INDEX]));

        StormParsingApplicationAttributes stormAttributes = new ObjectMapper()
                .readerFor(StormParsingApplicationAttributes.class)
                .readValue(stormAttributesStr);

        ParsingApplicationFactoryResult result = new ParsingApplicationFactoryImpl().create(parsingAttributesStr);
        if (result.getStatusCode() != ParsingApplicationFactoryResult.StatusCode.OK) {
            throw new IllegalArgumentException(result.getAttributes().getMessage());
        }

        ParsingApplicationFactoryAttributes parsingAttributes = result.getAttributes();

        Config config = new Config();
        config.putAll(stormAttributes.getStormAttributes().getStormConfig());
        StormTopology topology = createTopology(stormAttributes, parsingAttributes, new ZookeperConnectorFactory() {});
        String topologyName = String.format(TOPOLOGY_NAME_FORMAT_MSG, parsingAttributes.getName());
        LOG.info(SUBMIT_INFO_LOG, topologyName, stormAttributesStr, parsingAttributesStr);
        StormSubmitter.submitTopology(topologyName, config, topology);
    }
}

package plagueprocess;

import org.apache.flink.api.common.functions.FoldFunction;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.KeyedStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer010;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer010;
import org.apache.flink.streaming.util.serialization.AvroRowDeserializationSchema;
import org.apache.flink.streaming.util.serialization.AvroRowSerializationSchema;
import org.apache.flink.types.Row;

import java.util.HashMap;
import java.util.Properties;

public class PlagueProcess {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");

        AvroRowDeserializationSchema schema = new AvroRowDeserializationSchema(avro.Plague_Update.class);
        FlinkKafkaConsumer010<Row> myConsumer = new FlinkKafkaConsumer010<Row>("raw", schema, properties);

        myConsumer.setStartFromEarliest();
        DataStream<Row> stream = env.addSource(myConsumer);

        KeyedStream<Row, String> keyed = stream.keyBy(new KeySelector<Row, String>(){
            @Override
            public String getKey(Row row) throws Exception {
                return (String) row.getField(0);
            }
        });

        Row out = new Row(5);
        out.setField(0, "");
        out.setField(1, 0);
        out.setField(2, 0);
        out.setField(3, 0);
        out.setField(4, 0.0f);

        final HashMap<Object, Float> pop = new HashMap<>();
        pop.put("Witchita", 386552.0f);
        pop.put("Overland Park", 181260.0f);
        pop.put("Kansas City", 148483.0f);
        pop.put("Olathe", 131885.0f);
        pop.put("Topeka", 127678.0f);
        pop.put("Lawrence", 90811.0f);



        DataStream<Row> res = keyed
                .timeWindow(org.apache.flink.streaming.api.windowing.time.Time.seconds(1))
                .fold(out, new FoldFunction<Row, Row>() {

                    @Override
                    public Row fold(Row row, Row o) throws Exception {
                        row.setField(0, o.getField(0));
                        for(int i = 1; i < 4; i++)
                            row.setField(i, (Integer)o.getField(i) + (Integer)row.getField(i));
                        Float tmp = (Integer)o.getField(1) * (Integer)o.getField(3)
                                / pop.get(o.getField(0));
                        row.setField(4, (Float)o.getField(4) + tmp);
                        return row;
                    }
                });

        FlinkKafkaProducer010.FlinkKafkaProducer010Configuration<Row> con = FlinkKafkaProducer010.writeToKafkaWithTimestamps(
                res,
                "processed",
                new AvroRowSerializationSchema(avro.Plague_Update.class),
                properties);
        con.setWriteTimestampToKafka(false);

        env.execute();

    }
}
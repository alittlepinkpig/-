import io.netty.handler.codec.http2.Http2HeadersEncoder.Configuration
import org.apache.spark.sql.SparkSession
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.spark.streaming.{Seconds, StreamingContext}
import org.apache.spark.streaming.kafka010.LocationStrategies.PreferConsistent
import org.apache.spark.streaming.kafka010.ConsumerStrategies.Subscribe
import org.apache.spark.streaming.kafka010.KafkaUtils
import org.elasticsearch.hadoop.cfg.ConfigurationOptions
import org.elasticsearch.spark.streaming.EsSparkStreaming

object Streaming {
  def main(args: Array[String]): Unit = {
    val spark = SparkSession
      .builder()
      .master("local[3]")
      .appName("ELK")
      .config("some.spark.config.option","some.config")
      //设置启动序列化器，对数据流进行序列化
      .config("spark.serializer","org.apache.spark.serializer.KryoSerializer")
      //设置ES连接参数
      .config(ConfigurationOptions.ES_NODES,"ELKM")
      .config(ConfigurationOptions.ES_PORT,"9200")
      .getOrCreate()

    spark.sparkContext.setLogLevel("WARN")

    //时间窗口设置
    val ssc = new StreamingContext(spark.sparkContext,Seconds(60))
    val kafkaParams = Map(
      "bootstrap.servers"->"192.168.128.20:9092,192.168.128.21:9092,192.168.128.22:9092",
      "group.id"->"ELK",
      "key.deserializer"->classOf[StringDeserializer],
      "value.deserializer"->classOf[StringDeserializer],
      "enable.auto.commit"->"false"
//      "auto.offset.reset" -> "latest"
    )
    val topics = Set("house")

    val stream = KafkaUtils.createDirectStream[String, String](
      ssc,
      PreferConsistent,
      Subscribe[String, String](topics, kafkaParams)
    )
    //数据切分
    stream.map(x=>(x.offset(),x.value())).print()
    EsSparkStreaming.saveJsonToEs(stream.map(_.value()),"/house")

    ssc.start()
    ssc.awaitTermination()
  }
}

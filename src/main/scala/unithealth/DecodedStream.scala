package unithealth

import org.apache.spark.sql.functions.{from_json, udf}
import org.apache.spark.sql.types._
import org.apache.spark.sql.{DataFrame, SparkSession}

object DecodedStream {

  // Create JSON schema
  val schema = StructType(
    Seq(
      StructField("$type", StringType, true),
      StructField("DateTime", TimestampType, true),
      StructField("Longitude", DoubleType, true),
      StructField("Latitude", DoubleType, true),
      StructField("Speed", DoubleType, true),
      StructField("Heading", DoubleType, true),
      StructField("Odometer", DoubleType, true),
      StructField("IsIgnitionOn", BooleanType, true),
      StructField("EventId", IntegerType, true),
      StructField("DriverId", StringType, true),
      StructField("AccidentId", IntegerType, true),
      StructField("IsLastKnowLocation", BooleanType, true),
      StructField("Altitude", DoubleType, true),
      StructField("Battery", DoubleType, true),
      StructField("EngineHours", DoubleType, true),
      StructField("Address", StructType(Seq()), true),

      StructField("Forces", StructType(
        Seq(
          StructField("$type", StringType, true),
          StructField("$values", ArrayType(StructType(
            Seq(
              StructField("$type", StringType, true),
              StructField("Forward", IntegerType, true),
              StructField("Backward", IntegerType, true),
              StructField("Left", IntegerType, true),
              StructField("Right", IntegerType, true),
              StructField("Up", IntegerType, true),
              StructField("Down", IntegerType, true)
            )), true), true)
        )
      ), true),

      StructField("Samples", StructType(
        Seq(
          StructField("$type", StringType, true),
          StructField("$values", ArrayType(StructType(Seq()), true), true)
        )
      ), true),

      StructField("Id", StringType, true),
      StructField("Imei", LongType, true),
      StructField("VbuNo", IntegerType, true),
      StructField("UnitType", IntegerType, true),
      StructField("ServerDateTime", TimestampType, true)
    )
  )


  def getStream(spark : SparkSession, brokers : String, topic : String) : DataFrame = {

    import spark.implicits._

    // Construct a streaming DataFrame that reads from telemetry.locations
    val df = spark.readStream
      .format("kafka")
      .option("kafka.bootstrap.servers", brokers)
      .option("subscribe", topic)
      .option("startingOffsets", "latest")
      .option("minPartitions", "10")
      .option("failOnDataLoss", "true")
      .load()

    // create user defined unzip function
    val unzip = udf((a: Array[Byte]) => Gzip.decompress(a))

    // decode data
    val locationJSONdf = df.selectExpr("CAST(value AS STRING)").withColumn("value", unzip($"value"))

    // select from df and deserialize using json schema
    val locationNestedDf = locationJSONdf.select(from_json($"value", schema).as("location"))

    // flatten df
    val locFlattenedDf = locationNestedDf.selectExpr("location.DateTime",
      "location.Imei",
      "location.Longitude",
      "location.Latitude",
      "location.Speed",
      "location.Heading",
      "location.EventId")

    // result is flattened, decoded and deserialized location data
    locFlattenedDf

  }

}

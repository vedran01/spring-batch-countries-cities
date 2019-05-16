package com.bradara.vedran;


import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.item.data.MongoItemWriter;
import org.springframework.batch.item.data.builder.MongoItemWriterBuilder;
import org.springframework.batch.item.database.JdbcBatchItemWriter;
import org.springframework.batch.item.database.JpaPagingItemReader;
import org.springframework.batch.item.database.builder.JdbcBatchItemWriterBuilder;
import org.springframework.batch.item.database.builder.JpaPagingItemReaderBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.batch.item.json.JacksonJsonObjectMarshaller;
import org.springframework.batch.item.json.JacksonJsonObjectReader;
import org.springframework.batch.item.json.JsonFileItemWriter;
import org.springframework.batch.item.json.JsonItemReader;
import org.springframework.batch.item.json.builder.JsonFileItemWriterBuilder;
import org.springframework.batch.item.json.builder.JsonItemReaderBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.mongodb.core.MongoTemplate;

import javax.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import java.nio.file.Paths;

@RequiredArgsConstructor
@EnableBatchProcessing
@SpringBootApplication
public class StringBatchApplication {

	private final StepBuilderFactory stepBuilderFactory;
	private final JobBuilderFactory jobBuilderFactory;

	@Configuration
	static class Step1Configuration{
		@Bean
		JsonItemReader<Country> jsonCountryReader(@Value("${app.country-input}") Resource resource){
			return new JsonItemReaderBuilder<Country>()
							.name("country-reader")
							.jsonObjectReader(new JacksonJsonObjectReader<>(Country.class))
							.resource(resource)
							.build();
		}

		@Bean
		JdbcBatchItemWriter<Country> jdbcCountryWriter(@Autowired DataSource dataSource){
			return new JdbcBatchItemWriterBuilder<Country>()
							.dataSource(dataSource)
							.sql("INSERT INTO countries (name, alpha2code, alpha3code, calling_code) VALUES (:name, :alpha2Code, :alpha3Code, :callingCodes[0])")
							.beanMapped()
							.build();
		}
	}

	@Configuration
	static class Step2Configuration{

		@Bean
		FlatFileItemReader<City> csvCityReader(@Value("${app.city-input}") Resource resource){
			return new FlatFileItemReaderBuilder<City>()
							.name("city-reader")
							//.resource(new ClassPathResource("input/cities.csv"))
							.resource(resource )
							.linesToSkip(1)
							.targetType(City.class)
							.delimited()
							.delimiter(",")
							.includedFields(new Integer[]{0,2,3,6})
							.names(new String[]{"name","lat","lng", "alpha3Code"})
							.build();
		}

		@Bean
		JdbcBatchItemWriter<City> jdbcCityWriter(@Autowired DataSource dataSource){
			return new JdbcBatchItemWriterBuilder<City>()
							.dataSource(dataSource)
							.sql("INSERT INTO cities (name, lat, lng, country_id ) SELECT :name, :lat, :lng, c.id FROM countries c WHERE c.alpha3code=:alpha3Code")
							.beanMapped()
							.build();
		}
	}

	@Configuration
	static class Step3Configuration{
		@Autowired
		EntityManagerFactory entityManagerFactory;

		@Bean
		JpaPagingItemReader<Country> jpaCountryReader() {
			return new JpaPagingItemReaderBuilder<Country>()
							.name("country-city-reader")
							.entityManagerFactory(entityManagerFactory)
							.queryString("SELECT DISTINCT c from countries c INNER JOIN c.cities ct")
							.pageSize(10)
							.build();
		}

		@Bean
		JsonFileItemWriter<Country> jsonCountryWriter(@Value("${app.output}") String outputFile){
			return new JsonFileItemWriterBuilder<Country>()
							.name("json-writer")
							.resource(new FileSystemResource(Paths.get("").toAbsolutePath() + "/" + outputFile))
							.jsonObjectMarshaller(new JacksonJsonObjectMarshaller<>())
							.build();

		}
	}

	@Configuration
	static class Step4Configuration{
		@Autowired
		MongoTemplate mongoTemplate;
		@Bean
		MongoItemWriter<Country> mongoCountryWriter(){
			return new MongoItemWriterBuilder<Country>()
							.template(mongoTemplate)
							.collection("locations")
							.build();
		}
	}

	@Bean("step1")
	Step writeCountresToDb(@Autowired Step1Configuration configuration){
		return stepBuilderFactory.get("countries-to-db")
						.<Country,Country>chunk(100)
						.reader(configuration.jsonCountryReader(null))
						.writer(configuration.jdbcCountryWriter(null))
						.build();
	}

	@Bean("step2")
	Step writeCitiesToDb(@Autowired Step2Configuration configuration){
		return stepBuilderFactory.get("cities-to-db")
						.<City,City>chunk(1000)
						.reader(configuration.csvCityReader(null))
						.writer(configuration.jdbcCityWriter(null))
						.build();
	}

	@Bean("step3")
	Step writeCountriesToJson(@Autowired Step3Configuration configuration){
		return stepBuilderFactory.get("extract-to-json")
						.<Country, Country>chunk(10)
						.reader(configuration.jpaCountryReader())
						.writer(configuration.jsonCountryWriter(null))
						.build();
	}

	@Bean("step4")
	Step writeLocationsToMongo(@Autowired Step4Configuration conf, @Autowired Step3Configuration conf2){
		return stepBuilderFactory.get("locations-to-mongo")
						.<Country,Country>chunk(100)
						.reader(conf2.jpaCountryReader())
						.writer(conf.mongoCountryWriter())
						.build();
	}

	@Bean
	Job job1(@Autowired @Qualifier("step1") Step countriesToDB ,
							 @Autowired @Qualifier("step2") Step citiesToDB,
							 @Autowired @Qualifier("step3") Step countriesToJson,
					      @Autowired @Qualifier("step4") Step locationsToMongo
	){

		return jobBuilderFactory.get("job1")
						.incrementer(new RunIdIncrementer())
						.start(countriesToDB)
						.next(citiesToDB)
						.next(countriesToJson)
						.next(locationsToMongo)
						.build();
	}

	public static void main(String[] args){
		SpringApplication.run(StringBatchApplication.class, args);
	}

}

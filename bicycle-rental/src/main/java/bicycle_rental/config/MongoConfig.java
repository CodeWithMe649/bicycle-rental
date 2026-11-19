package bicycle_rental.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;

@Configuration
public class MongoConfig {

    // Automatically enables multi-document transactions when deployed to MongoDB Atlas (mongodb+srv://)
    // and disables it on standalone localhost so local testing never throws IllegalOperation errors.
    @Bean
    @ConditionalOnExpression("'${spring.data.mongodb.uri:}'.startsWith('mongodb+srv://')")
    MongoTransactionManager transactionManager(MongoDatabaseFactory dbFactory) {
        return new MongoTransactionManager(dbFactory);
    }
}
package com.aiantfarm.config;

import com.aiantfarm.repository.*;
import com.aiantfarm.repository.dynamo.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;

/**
 * Wires repository interfaces to their DynamoDB implementations.
 */
@Configuration
public class RepositoryConfig {

  @Value("${antfarm.tables.main:antfarm_main}")
  private String tableName;

  @Bean
  public UserRepository userRepository(DynamoDbEnhancedClient enhanced) {
    return new UserRepositoryImpl(enhanced, tableName);
  }

  @Bean
  public AuthCredentialsRepository authCredentialsRepository(DynamoDbEnhancedClient enhanced) {
    return new AuthCredentialsRepositoryImpl(enhanced, tableName);
  }

  @Bean
  public PasswordResetTokenRepository passwordResetTokenRepository(DynamoDbEnhancedClient enhanced) {
    return new PasswordResetTokenRepositoryImpl(enhanced, tableName);
  }

  @Bean
  public RoomRepository roomRepository(DynamoDbEnhancedClient enhanced) {
    return new RoomRepositoryImpl(enhanced, tableName);
  }

  @Bean
  public MessageRepository messageRepository(DynamoDbEnhancedClient enhanced) {
    return new MessageRepositoryImpl(enhanced, tableName);
  }

  @Bean
  public RoomAntRoleRepository roomAntRoleRepository(DynamoDbEnhancedClient enhanced) {
    return new RoomAntRoleRepositoryImpl(enhanced, tableName);
  }

  // --- Ants ---

  @Bean
  public AntRepository antRepository(DynamoDbEnhancedClient enhanced) {
    return new AntRepositoryImpl(enhanced, tableName);
  }

  @Bean
  public AntRoomAssignmentRepository antRoomAssignmentRepository(DynamoDbEnhancedClient enhanced) {
    return new AntRoomAssignmentRepositoryImpl(enhanced, tableName);
  }
}

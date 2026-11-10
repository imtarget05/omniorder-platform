package com.omniorder.inventory.config;

import com.omniorder.inventory.grpc.InventoryGrpcService;
import io.grpc.Server;
import io.grpc.netty.shaded.io.grpc.netty.NettyServerBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class GrpcServerConfig {

    @Bean
    @ConditionalOnProperty(name = "grpc.server.enabled", havingValue = "true", matchIfMissing = false)
    public Server grpcServer(InventoryGrpcService inventoryGrpcService,
                             @Value("${spring.grpc.server.port:9090}") int port) throws IOException {
        Server server = NettyServerBuilder.forPort(port)
            .addService(inventoryGrpcService)
            .build();
        server.start();
        return server;
    }
}

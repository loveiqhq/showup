import { Module } from '@nestjs/common';
import { ConfigModule, ConfigService } from '@nestjs/config';
import { TypeOrmModule } from '@nestjs/typeorm';

/**
 * Wires TypeORM to PostgreSQL using validated config. `synchronize` is always false — schema
 * changes go through explicit migrations (see src/database/migrations). Entities are auto-loaded
 * as feature modules register them. Set DB_MIGRATIONS_RUN=true to apply migrations on boot.
 */
@Module({
  imports: [
    TypeOrmModule.forRootAsync({
      imports: [ConfigModule],
      inject: [ConfigService],
      useFactory: (config: ConfigService) => ({
        type: 'postgres' as const,
        host: config.get<string>('database.host'),
        port: config.get<number>('database.port'),
        username: config.get<string>('database.user'),
        password: config.get<string>('database.password'),
        database: config.get<string>('database.name'),
        // TLS for managed databases; verification stays ON. For a private CA, set
        // NODE_EXTRA_CA_CERTS rather than disabling verification.
        ssl: config.get<boolean>('database.ssl') ?? false,
        autoLoadEntities: true,
        migrations: [__dirname + '/migrations/*{.ts,.js}'],
        migrationsRun: config.get<boolean>('database.migrationsRun'),
        synchronize: false,
        logging: config.get<boolean>('database.logging'),
      }),
    }),
  ],
})
export class DatabaseModule {}

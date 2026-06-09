package dev.invisiblespiders.haven.core.service;

import dev.invisiblespiders.haven.api.event.HavenEconomyTransactionEvent;
import dev.invisiblespiders.haven.core.config.EconomySettings;
import dev.invisiblespiders.haven.core.economy.ItemEconomyAdapter;
import dev.invisiblespiders.haven.core.economy.MoneyEconomyAdapter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EconomyServiceImplTest {

    @Test
    void reportsConfiguredPreferredAdapter() {
        EconomyServiceImpl service = new EconomyServiceImpl(
            mock(MoneyEconomyAdapter.class),
            mock(ItemEconomyAdapter.class),
            new EventBusImpl(),
            EconomySettings.Adapter.ITEM
        );

        assertEquals("item", service.getPreferredAdapter());
    }

    @Test
    void depositDoesNotPublishTransactionEventWhenMoneyDepositDoesNotSucceed() {
        EventBusImpl eventBus = new EventBusImpl();
        List<HavenEconomyTransactionEvent> events = new ArrayList<>();
        eventBus.subscribe(HavenEconomyTransactionEvent.class, events::add);

        EconomyServiceImpl service = new EconomyServiceImpl(
            mock(MoneyEconomyAdapter.class),
            mock(ItemEconomyAdapter.class),
            eventBus
        );

        boolean deposited = service.deposit(UUID.randomUUID(), 25.0);

        assertFalse(deposited);
        assertTrue(events.isEmpty());
    }

    @Test
    void moneyOpsReturnFalseWhenItemAdapterIsPreferred() {
        MoneyEconomyAdapter money = mock(MoneyEconomyAdapter.class);
        EconomyServiceImpl service = new EconomyServiceImpl(
            money,
            mock(ItemEconomyAdapter.class),
            new EventBusImpl(),
            EconomySettings.Adapter.ITEM
        );
        UUID uuid = UUID.randomUUID();
        assertFalse(service.has(uuid, 10.0));
        assertFalse(service.withdraw(uuid, 10.0));
        assertFalse(service.deposit(uuid, 10.0));
        verifyNoInteractions(money);
    }

    @Test
    void depositPublishesTransactionEventWhenMoneyDepositSucceeds() {
        EventBusImpl eventBus = new EventBusImpl();
        List<HavenEconomyTransactionEvent> events = new ArrayList<>();
        eventBus.subscribe(HavenEconomyTransactionEvent.class, events::add);

        UUID playerUuid = UUID.randomUUID();
        MoneyEconomyAdapter money = mock(MoneyEconomyAdapter.class);
        when(money.deposit(playerUuid, 25.0)).thenReturn(true);

        EconomyServiceImpl service = new EconomyServiceImpl(
            money,
            mock(ItemEconomyAdapter.class),
            eventBus
        );

        boolean deposited = service.deposit(playerUuid, 25.0);

        assertTrue(deposited);
        assertEquals(1, events.size());
        HavenEconomyTransactionEvent event = events.getFirst();
        assertEquals(playerUuid, event.getPlayerUuid());
        assertEquals(25.0, event.getAmount());
        assertEquals(HavenEconomyTransactionEvent.Type.DEPOSIT, event.getType());
    }
}

package com.shastage.ujin.domain.service;

import com.shastage.ujin.datasource.entity.CommandEntity;
import com.shastage.ujin.datasource.repository.jpa.*;
import com.shastage.ujin.domain.exception.CatalogException;
import com.shastage.ujin.domain.repository.CatalogStore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.data.domain.PageRequest;
import java.time.Instant;
import java.util.*;

/** Only designated owner/UUIDs can reach the operator's separate LOCAL pair. */
@Service
public class PythonEmulatorService {
    private final PythonEmulatorProperties config;
    private final PythonEmulatorGateway gateway;
    private final CatalogService catalog;
    private final CatalogStore store;
    private final LocalRuntimeService runtime;
    private final LocalSignalStateJpaRepository states;
    private final EventJpaRepository events;
    private final CommandJpaRepository commands;
    private final RunJpaRepository runs;
    private final TransactionTemplate tx;
    public PythonEmulatorService(PythonEmulatorProperties config, PythonEmulatorGateway gateway,
            CatalogService catalog, CatalogStore store, LocalRuntimeService runtime,
            LocalSignalStateJpaRepository states, EventJpaRepository events,
            CommandJpaRepository commands, RunJpaRepository runs, PlatformTransactionManager manager) {
        this.config=config;this.gateway=gateway;this.catalog=catalog;this.store=store;this.runtime=runtime;
        this.states=states;this.events=events;this.commands=commands;this.runs=runs;this.tx=new TransactionTemplate(manager);
    }
    private void requireOwner(String owner) {
        if(!config.isEnabled())throw new CatalogException(503,"PYTHON_DISABLED","Внешний LOCAL-Python стенд выключен");
        if(!config.getOwner().equals(owner))throw new CatalogException(404,"DEVICE_NOT_FOUND","Device not found");
        if(config.getSourceDeviceId()==null || config.getTargetDeviceId()==null || config.getSourceDeviceId().equals(config.getTargetDeviceId()))
            throw new CatalogException(503,"PYTHON_NOT_CONFIGURED","Не заданы отдельные UUID датчика и лампы на сервере");
        validate(config.getSourceDeviceId(),owner,true);validate(config.getTargetDeviceId(),owner,false);
    }
    private void validate(UUID id,String owner,boolean source) {
        var device=catalog.device(id,owner);String key=source?"leak":"lamp";
        if(!(source?"leak":"light").equals(device.family()))throw new CatalogException(422,"PYTHON_DECLARATION_INVALID","Неверный тип назначенного Python-устройства");
        var signal=device.signals().stream().filter(s->key.equals(s.key())).findFirst().orElseThrow(()->new CatalogException(422,"PYTHON_DECLARATION_INVALID","Нет сигнала Python-устройства"));
        if(!"NUMBER".equals(signal.valueType()) || (source?"w".equals(signal.management()):"r".equals(signal.management())))
            throw new CatalogException(422,"PYTHON_DECLARATION_INVALID","Неверные права сигнала Python-устройства");
    }
    public Map<String,Object> state(UUID id,String owner) {
        requireOwner(owner);boolean source=config.source(id,owner);
        if(!source&&!config.target(id,owner))throw new CatalogException(404,"DEVICE_NOT_FOUND","Device not found");
        double value=gateway.read(source?config.getLeakUrl():config.getLightUrl(),source?"leak":"light",source?"leak":"lamp");
        var result=new LinkedHashMap<>(runtime.getDeviceState(id,"LOCAL",owner));String observed=Instant.now().toString();
        result.put("provider","PYTHON");result.put("observedAt",observed);
        result.put("values",List.of(Map.of("signalKey",source?"leak":"lamp","value",value,"updatedAt",observed)));
        return result;
    }
    private final java.util.concurrent.locks.ReentrantLock observation = new java.util.concurrent.locks.ReentrantLock();
    public Map<String,Object> poll(UUID eventId,String owner) {
        observation.lock();
        try { return pollSerial(eventId,owner); } finally { observation.unlock(); }
    }
    private Map<String,Object> pollSerial(UUID eventId,String owner) {
        requireOwner(owner);
        if(eventId==null)throw new CatalogException(422,"VALIDATION_ERROR","eventId is required");
        var previous=events.findByEventIdAndOwner(eventId,owner);
        if(previous.isPresent()) {
            if(!previous.get().getDeviceId().equals(config.getSourceDeviceId()))throw new CatalogException(409,"IDEMPOTENCY_CONFLICT","Event belongs to a different source");
            dispatchEvent(eventId,owner);return runtime.getEvent(eventId,owner);
        }
        // Read outside transaction; validate declaration versions again under the lock.
        long sourceVersion=catalog.device(config.getSourceDeviceId(),owner).version();
        long targetVersion=catalog.device(config.getTargetDeviceId(),owner).version();
        double value=gateway.read(config.getLeakUrl(),"leak","leak");
        tx.execute(s->{
            store.lock();requireOwner(owner);
            if(catalog.device(config.getSourceDeviceId(),owner).version()!=sourceVersion || catalog.device(config.getTargetDeviceId(),owner).version()!=targetVersion)
                throw new CatalogException(409,"VERSION_CONFLICT","Декларация изменилась во время чтения эмулятора");
            // A concurrent identical poll returns the existing receipt, no new observation.
            if(events.existsByEventId(eventId)) {
                var existing=events.findByEventIdAndOwner(eventId,owner).orElseThrow(()->new CatalogException(404,"EVENT_NOT_FOUND","Event not found"));
                if(!existing.getDeviceId().equals(config.getSourceDeviceId()))throw new CatalogException(409,"IDEMPOTENCY_CONFLICT","Event belongs to a different source");
                return runtime.getEvent(eventId,owner);
            }
            // First observation establishes a baseline; processStimulus then records NO_STATE_CHANGE.
            if(states.findByDeviceIdAndSignalKeyAndOwner(config.getSourceDeviceId(),"leak",owner).isEmpty())
                runtime.applyEmulatorValue(config.getSourceDeviceId(),"leak",value,owner);
            return runtime.processStimulus(config.getSourceDeviceId(),eventId,"WET",(int)value,owner);
        });
        dispatchEvent(eventId,owner);return runtime.getEvent(eventId,owner);
    }
    public void dispatchEvent(UUID eventId,String owner) {
        if(!config.isEnabled() || !config.getOwner().equals(owner))return;
        int page=0;
        org.springframework.data.domain.Page<com.shastage.ujin.datasource.entity.RunEntity> found;
        do {
            found=runs.findByOwnerAndEventId(owner,eventId,PageRequest.of(page++,100));
            for(var run:found)if(config.target(run.getActionDeviceId(),owner) && commands.findByCommandIdAndOwner(run.getId(),owner).isPresent())dispatch(run.getId(),owner);
        }while(found.hasNext());
    }
    public Map<String,Object> dispatch(UUID id,String owner) {
        if(!config.isEnabled() || !config.getOwner().equals(owner))return runtime.getCommand(id,owner);
        CommandEntity reserved=tx.execute(s->{
            store.lock();var c=commands.findByCommandIdAndOwner(id,owner).orElse(null);
            if(c==null || !config.target(c.getDeviceId(),owner) || !"LOCAL".equals(c.getExecutionMode()) || !"PYTHON_QUEUED".equals(c.getReason()))return null;
            requireOwner(owner);c.setReason("PYTHON_DISPATCHING");c.setUpdatedAt(Instant.now().toString());commands.saveAndFlush(c);return c;
        });
        if(reserved==null)return runtime.getCommand(id,owner);
        try {gateway.command(config.getLightUrl(),reserved.getValue());}
        catch(CatalogException failure){return finish(id,owner,"PYTHON_REJECTED".equals(failure.code)?"FAILED":"UNKNOWN","PYTHON_REJECTED".equals(failure.code)?"PYTHON_COMMAND_REJECTED":"PYTHON_COMMAND_UNKNOWN");}
        finish(id,owner,"PENDING","PYTHON_READBACK_PENDING");
        return check(id,owner);
    }
    public Map<String,Object> check(UUID id,String owner) {
        requireOwner(owner);var c=commands.findByCommandIdAndOwner(id,owner).orElseThrow(()->new CatalogException(404,"COMMAND_NOT_FOUND","Command not found"));
        if(!config.target(c.getDeviceId(),owner)||!"LOCAL".equals(c.getExecutionMode()))throw new CatalogException(404,"COMMAND_NOT_FOUND","Command not found");
        if("CONFIRMED".equals(c.getStatus())||"FAILED".equals(c.getStatus())||c.getResolutionNote()!=null)return runtime.getCommand(id,owner);
        if("PYTHON_DISPATCHING".equals(c.getReason()) && Instant.parse(c.getUpdatedAt()).plusSeconds(15).isBefore(Instant.now()))
            return finish(id,owner,"UNKNOWN","PYTHON_DISPATCH_INTERRUPTED");
        // A lost POST response must not be confirmed from an already matching old value.
        if(c.getReason()==null || !Set.of("PYTHON_READBACK_PENDING","PYTHON_READBACK_MISMATCH","PYTHON_READBACK_UNAVAILABLE").contains(c.getReason()))return runtime.getCommand(id,owner);
        try {return gateway.read(config.getLightUrl(),"light","lamp")==c.getValue()
                ?finish(id,owner,"CONFIRMED","PYTHON_READBACK_CONFIRMED"):finish(id,owner,"UNKNOWN","PYTHON_READBACK_MISMATCH");}
        catch(CatalogException failure){return finish(id,owner,"UNKNOWN","PYTHON_READBACK_UNAVAILABLE");}
    }
    private Map<String,Object> finish(UUID id,String owner,String status,String reason) {
        return tx.execute(s->{
            store.lock();var c=commands.findByCommandIdAndOwner(id,owner).orElseThrow();
            if("CONFIRMED".equals(c.getStatus())||"FAILED".equals(c.getStatus())||c.getResolutionNote()!=null)return runtime.getCommand(id,owner);
            c.setStatus(status);c.setReason(reason);c.setUpdatedAt(Instant.now().toString());
            if("CONFIRMED".equals(status)){c.setConfirmedAt(Instant.now().toString());runtime.applyEmulatorValue(c.getDeviceId(),c.getSignalKey(),c.getValue(),owner);}
            commands.saveAndFlush(c);
            runs.findByIdAndOwner(id,owner).ifPresent(run->{
                run.setStatus("CONFIRMED".equals(status)?"SUCCEEDED":status);run.setReason(reason);
                run.addEntry("RESULT",reason,Instant.now().toString());runs.saveAndFlush(run);
            });
            return runtime.getCommand(id,owner);
        });
    }
}

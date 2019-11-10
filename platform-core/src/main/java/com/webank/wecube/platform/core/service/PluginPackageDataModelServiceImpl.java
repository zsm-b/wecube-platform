package com.webank.wecube.platform.core.service;

import com.google.common.base.Splitter;
import com.google.common.collect.Iterables;
import com.webank.wecube.platform.core.commons.WecubeCoreException;
import com.webank.wecube.platform.core.domain.plugin.PluginPackage;
import com.webank.wecube.platform.core.domain.plugin.PluginPackageAttribute;
import com.webank.wecube.platform.core.domain.plugin.PluginPackageDataModel;
import com.webank.wecube.platform.core.domain.plugin.PluginPackageEntity;
import com.webank.wecube.platform.core.dto.PluginPackageAttributeDto;
import com.webank.wecube.platform.core.dto.PluginPackageDataModelDto;
import com.webank.wecube.platform.core.dto.PluginPackageEntityDto;
import com.webank.wecube.platform.core.jpa.PluginPackageAttributeRepository;
import com.webank.wecube.platform.core.jpa.PluginPackageEntityRepository;
import com.webank.wecube.platform.core.jpa.PluginPackageDataModelRepository;
import com.webank.wecube.platform.core.jpa.PluginPackageRepository;
import com.webank.wecube.platform.core.support.PluginPackageDataModelHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;

import java.util.*;

import static com.google.common.collect.Sets.newLinkedHashSet;

@Service
@Transactional
public class PluginPackageDataModelServiceImpl implements PluginPackageDataModelService {

    @Autowired
    PluginPackageDataModelRepository dataModelRepository;
    @Autowired
    PluginPackageEntityRepository pluginPackageEntityRepository;
    @Autowired
    PluginPackageAttributeRepository pluginPackageAttributeRepository;
    @Autowired
    PluginPackageRepository pluginPackageRepository;

    private static final Logger logger = LoggerFactory.getLogger(PluginPackageDataModelServiceImpl.class);

    @Override
    public PluginPackageDataModelDto register(PluginPackageDataModelDto pluginPackageDataModelDto) {
        /*
        1. make sure related PluginPackage exists
        2. DataModel1=Dto.toDomain(). DateModel2=getLatestDataModelForPluginPackage(packageName), if dataModel1.equals(dataModel2), throws exception.
        3. update DataModel1.version = DateModel2.version + 1
        4. buildReferenceNameMap
        5. buildNameToAttributeMap
        6. Update DataModel1
         */
        Optional<PluginPackage> latestPluginPackageByPackageName = pluginPackageRepository.findTop1ByNameOrderByVersionDesc(
                pluginPackageDataModelDto.getPackageName());

        if (!latestPluginPackageByPackageName.isPresent()) {
            String msg = String.format("Cannot find the package [%s] while registering data model", pluginPackageDataModelDto.getPackageName());
            logger.error(msg);
            throw new WecubeCoreException(msg);
        }

        PluginPackageDataModel transferredPluginPackageDataModel = PluginPackageDataModelDto.toDomain(pluginPackageDataModelDto);
        Optional<PluginPackageDataModel> pluginPackageDataModelOptional = dataModelRepository.findLatestDataModelByPackageName(pluginPackageDataModelDto.getPackageName());
        if (pluginPackageDataModelOptional.isPresent()) {
            PluginPackageDataModel dataModelFromDatabase = pluginPackageDataModelOptional.get();
            if (PluginPackageDataModelHelper.isDataModelSameAsAnother(transferredPluginPackageDataModel, dataModelFromDatabase)) {
                throw new WecubeCoreException("Refreshed data model is same as existing latest one.");
            }
            transferredPluginPackageDataModel.setVersion(dataModelFromDatabase.getVersion() + 1);
        }

        Map<String, String> attributeReferenceNameMap = buildAttributeReferenceNameMap(pluginPackageDataModelDto);
        Map<String, PluginPackageAttribute> referenceAttributeMap = buildReferenceAttributeMap(transferredPluginPackageDataModel);
        updateAttributeReference(transferredPluginPackageDataModel, attributeReferenceNameMap, referenceAttributeMap);

        PluginPackageDataModel savedPluginPackageDataModel = dataModelRepository.save(transferredPluginPackageDataModel);

        return PluginPackageDataModelDto.fromDomain(savedPluginPackageDataModel);
    }

    private Map<String, PluginPackageAttribute> buildReferenceAttributeMap(PluginPackageDataModel transferredPluginPackageDataModel) {
        Map<String, PluginPackageAttribute> nameToAttributeMap = new HashMap<>();
        transferredPluginPackageDataModel.getPluginPackageEntities()
                .forEach(entity->
                        entity.getPluginPackageAttributeList()
                                .forEach(attribute->
                                        nameToAttributeMap.put(entity.getPackageName()+"'" + entity.getName() + "'" + attribute.getName(), attribute)));

        return nameToAttributeMap;
    }

    private Map<String, String> buildAttributeReferenceNameMap(PluginPackageDataModelDto pluginPackageDataModelDto) {
        Map<String, String> attributeReferenceNameMap = new HashMap<>();
        pluginPackageDataModelDto.getPluginPackageEntities().forEach(entityDto->
            entityDto.getAttributes()
                    .stream()
                    .filter(attribute->"ref".equals(attribute.getDataType()))
                    .forEach(attribute->
                            attributeReferenceNameMap.put(entityDto.getPackageName()+"'"+entityDto.getName()+"'"+attribute.getName(),
                                    attribute.getRefPackageName() + "'" + attribute.getRefEntityName() + "'" + attribute.getRefAttributeName())));
        return attributeReferenceNameMap;
    }

    @Override
    public Set<PluginPackageDataModelDto> allDataModels() {
        Set<PluginPackageDataModelDto> pluginPackageDataModelDtos = newLinkedHashSet();
        Optional<List<String>> allPackageNamesOptional = pluginPackageRepository.findAllDistinctPackage();
        allPackageNamesOptional.ifPresent(allPackageNames -> {
            for (String packageName : allPackageNames) {
                Optional<PluginPackageDataModel> pluginPackageDataModelOptional = dataModelRepository.findLatestDataModelByPackageName(packageName);
                if (pluginPackageDataModelOptional.isPresent()) {
                    pluginPackageDataModelDtos.add(PluginPackageDataModelDto.fromDomain(pluginPackageDataModelOptional.get()));
                }
            }
        });
        return pluginPackageDataModelDtos;
    }

    @Override
    public PluginPackageDataModelDto dataModelByPackageName(String packageName) {
        return null;
    }

    /**
     * Plugin model overview
     *
     * @return an list of entity dtos which contain both entities and attributes
     */
    @Override
    public List<PluginPackageEntityDto> overview() {
        Set<PluginPackageEntity> packageEntities = newLinkedHashSet();
        Optional<List<String>> allPackageNamesOptional = pluginPackageRepository.findAllDistinctPackage();
        allPackageNamesOptional.ifPresent(allPackageNames -> {
            for (String packageName : allPackageNames) {
                Optional<PluginPackageDataModel> pluginPackageDataModelOptional = dataModelRepository.findLatestDataModelByPackageName(packageName);
                if (pluginPackageDataModelOptional.isPresent()) {
                    PluginPackageDataModel dataModel = pluginPackageDataModelOptional.get();
                    packageEntities.addAll(dataModel.getPluginPackageEntities());
                }
            }
        });
        return convertEntityDomainToDto(packageEntities, true);
    }

    /**
     * View one data model entity with its relationship by packageName
     *
     * @param packageName the name of package
     * @return list of entity dto
     */
    @Override
    public List<PluginPackageEntityDto> packageView(String packageName) throws WecubeCoreException {
        Optional<PluginPackage> latestPluginPackageByName = pluginPackageRepository.findLatestVersionByName(packageName);
        if (!latestPluginPackageByName.isPresent()) {
            String msg = String.format("Plugin package with name [%s] is not found", packageName);
            logger.info(msg);
            return Collections.emptyList();
        }
        Optional<PluginPackageDataModel> latestDataModelByPackageName = dataModelRepository.findLatestDataModelByPackageName(packageName);
        if (!latestDataModelByPackageName.isPresent()) {
            String errorMessage = String.format("Data model not found for package name=[%s]", packageName);
            logger.error(errorMessage);
            throw new WecubeCoreException(errorMessage);
        }

        return convertEntityDomainToDto(latestDataModelByPackageName.get().getPluginPackageEntities(), true);
    }

    /**
     * Update candidate entity list according to the reference mapping
     *
     * @param pluginPackageDataModel the candidate pluginPackageDataModel, will be inserted to DB later
     * @param referenceNameMap    map "{package}`{entity}`{attribute}" to another "{package}`{entity}`{attribute}"
     * @param nameToAttributeMap  map "{package}`{entity}`{attribute}" to attribute domain object
     * @throws WecubeCoreException when reference name the dto passed is invalid
     */
    private void updateAttributeReference(PluginPackageDataModel pluginPackageDataModel, Map<String, String> referenceNameMap,
                                          Map<String, PluginPackageAttribute> nameToAttributeMap) throws WecubeCoreException {
        // update the attribtue domain object with pre-noted map
        for (PluginPackageEntity candidateEntity : pluginPackageDataModel.getPluginPackageEntities()) {

            for (PluginPackageAttribute pluginPackageAttribute : candidateEntity
                    .getPluginPackageAttributeList()) {
                String selfName = pluginPackageAttribute.getPluginPackageEntity().getPackageName() + "`"
                        + pluginPackageAttribute.getPluginPackageEntity().getName() + "`"
                        + pluginPackageAttribute.getName();
                if (referenceNameMap.containsKey(selfName)) {
                    // only need to assign the attribute to attribute when the selfName is found in referenceNameMap
                    String referenceName = referenceNameMap.get(selfName);
                    // check nameToAttributeMap first, if not exist, then check the database, finally throw the exception
                    if (nameToAttributeMap.containsKey(referenceName)) {
                        // the reference is inside the same package
                        PluginPackageAttribute referenceAttribute = nameToAttributeMap
                                .get(referenceName);
                        pluginPackageAttribute.setPluginPackageAttribute(referenceAttribute);
                    } else {
                        // cross-package reference process
                        // the reference cannot be found in the referenceNameMap
                        // should search from the database with latest package version

                        // split the crossReferenceName
                        Iterable<String> splitResult = Splitter.on('`').trimResults().split(referenceName);
                        if (Iterables.size(splitResult) != 3) {
                            String msg = String.format("The reference name [%s] is illegal", referenceName);
                            logger.error(msg);
                            throw new WecubeCoreException(msg);
                        }
                        // fetch the packageName, packageVersion, entityName, attributeName
                        Iterator<String> splitResultIterator = splitResult.iterator();
                        String referencePackageName = splitResultIterator.next();
                        String referenceEntityName = splitResultIterator.next();
                        String referenceAttributeName = splitResultIterator.next();
                        Optional<PluginPackageDataModel> latestDataModelByPackageName = dataModelRepository.findLatestDataModelByPackageName(referencePackageName);
                        if (!latestDataModelByPackageName.isPresent()) {
                            String msg = String.format("Cannot found the specified data model with package name: [%s]", referencePackageName);
                            logger.error(msg);
                            throw new WecubeCoreException(msg);
                        }
                        PluginPackageDataModel dataModel = latestDataModelByPackageName.get();
                        Optional<PluginPackageEntity> foundReferenceEntityOptional = dataModel.getPluginPackageEntities().stream().filter(entity -> referenceEntityName.equals(entity.getName())).findAny();

                        if (!foundReferenceEntityOptional.isPresent()) {
                            String msg = String.format("Cannot found the specified plugin model entity with package name: [%s], entity name: [%s]", referencePackageName, referenceEntityName);
                            logger.error(msg);
                            throw new WecubeCoreException(msg);
                        }

                        Optional<PluginPackageAttribute> pluginPackageAttributeOptional = foundReferenceEntityOptional.get().getPluginPackageAttributeList().stream().filter(attribute -> referenceAttributeName.equals(attribute.getName())).findAny();
                        if (pluginPackageAttributeOptional.isPresent()) {
                            pluginPackageAttribute.setPluginPackageAttribute(pluginPackageAttributeOptional.get());
                        } else {
                            String msg = String.format(
                                    "Cannot found the specified plugin model attribute with package name: [%s], entity name: [%s], attribute name: [%s]",
                                    referencePackageName, referenceEntityName, referenceAttributeName);
                            logger.error(msg);
                            throw new WecubeCoreException(msg);
                        }
                    }
                }
            }
        }
    }

    /**
     * Update the reference info for both reference by and reference to
     * This feature is for entity to known whom it refers to and whom it is referred by
     *
     * @param inputEntityDtoList entity dto list as input
     */
    private void updateReferenceInfo(List<PluginPackageEntityDto> inputEntityDtoList) {
        for (PluginPackageEntityDto inputEntityDto : inputEntityDtoList) {
            // query for the referenceBy info
            String packageName = inputEntityDto.getPackageName();
            String entityName = inputEntityDto.getName();
            int dataModelVersion = 0;

            Optional<PluginPackageDataModel> latestDataModelByPackageName = dataModelRepository.findLatestDataModelByPackageName(packageName);
            if (latestDataModelByPackageName.isPresent()) {
                dataModelVersion = latestDataModelByPackageName.get().getVersion();
            }
            // find "reference by" info by latest data model version
            Optional<List<PluginPackageAttribute>> allAttributeReferenceByList = pluginPackageAttributeRepository.findAllReferenceByAttribute(packageName, entityName, dataModelVersion);

            allAttributeReferenceByList.ifPresent(attributeList -> attributeList.forEach(attribute -> {
                // the process of found reference by info
                PluginPackageEntity referenceByEntity = attribute.getPluginPackageEntity();
                if (!packageName.equals(referenceByEntity.getPackageName()) ||
                        !entityName.equals(referenceByEntity.getName())) {
                    // only add the dto to set when the attribute doesn't belong to this input entity
                    inputEntityDto.updateReferenceBy(
                            referenceByEntity.getId(),
                            referenceByEntity.getPackageName(),
                            referenceByEntity.getDataModelVersion(),
                            referenceByEntity.getName(),
                            referenceByEntity.getDisplayName());
                }
            }));

            // query for the referenceTo info
            List<PluginPackageAttributeDto> attributes = inputEntityDto.getAttributes();
            if (!CollectionUtils.isEmpty(attributes)) {
                attributes.forEach(attributeDto-> {
                            dataModelRepository.findLatestDataModelByPackageName(attributeDto.getRefPackageName()).ifPresent(dataModel ->
                                    dataModel.getPluginPackageEntities().stream().filter(entity -> attributeDto.getRefEntityName().equals(entity.getName())).findAny().ifPresent(entity -> {
                                        PluginPackageEntityDto entityReferenceToDto = PluginPackageEntityDto.fromDomain(entity);
                                        inputEntityDto.updateReferenceTo(
                                                entityReferenceToDto.getId(),
                                                entityReferenceToDto.getPackageName(),
                                                entityReferenceToDto.getDataModelVersion(),
                                                entityReferenceToDto.getName(),
                                                entityReferenceToDto.getDisplayName()
                                        );
                                    }));
                        }
                );
            }
        }
    }

    /**
     * Convert the plugin model entities from domains to dtos
     *
     * @param savedPluginPackageEntity an Iterable pluginPackageEntity
     * @return converted dtos
     */
    private List<PluginPackageEntityDto> convertEntityDomainToDto(Iterable<PluginPackageEntity> savedPluginPackageEntity, boolean ifUpdateReferenceInfo) {
        List<PluginPackageEntityDto> pluginPackageEntityDtos = new ArrayList<>();
        savedPluginPackageEntity
                .forEach(domain -> pluginPackageEntityDtos.add(PluginPackageEntityDto.fromDomain(domain)));
        if (ifUpdateReferenceInfo) updateReferenceInfo(pluginPackageEntityDtos);

        return pluginPackageEntityDtos;
    }

    @Override
    public PluginPackageDataModelDto pullDynamicDataModel(String packageName) {
        Optional<PluginPackage> latestPluginPackageByName = pluginPackageRepository.findLatestVersionByName(packageName);
        if (!latestPluginPackageByName.isPresent()) {
            String errorMessage = String.format("Plugin package with name [%s] is not found", packageName);
            logger.error(errorMessage);
            throw new WecubeCoreException(errorMessage);
        }
        Optional<PluginPackageDataModel> latestDataModelByPackageName = dataModelRepository.findLatestDataModelByPackageName(packageName);
        if (!latestDataModelByPackageName.isPresent()) {
            String errorMessage = String.format("Data model not found for package name=[%s]", packageName);
            logger.error(errorMessage);
            throw new WecubeCoreException(errorMessage);
        }

        return null;
    }
}

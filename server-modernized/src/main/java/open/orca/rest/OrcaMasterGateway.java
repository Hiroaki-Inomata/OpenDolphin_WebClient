package open.orca.rest;

public interface OrcaMasterGateway {
    OrcaMasterDao.GenericClassSearchResult searchGenericClass(OrcaMasterDao.GenericClassCriteria criteria);

    OrcaMasterDao.ListSearchResult<OrcaMasterDao.DrugRecord> searchDrug(OrcaMasterDao.DrugCriteria criteria);

    OrcaMasterDao.ListSearchResult<OrcaMasterDao.CommentRecord> searchComment(OrcaMasterDao.CommentCriteria criteria);

    OrcaMasterDao.ListSearchResult<OrcaMasterDao.CommentRecord> searchBodypart(OrcaMasterDao.CommentCriteria criteria);

    OrcaMasterDao.ListSearchResult<OrcaMasterDao.YouhouRecord> searchYouhou(OrcaMasterDao.YouhouCriteria criteria);

    OrcaMasterDao.ListSearchResult<OrcaMasterDao.MaterialRecord> searchMaterial(OrcaMasterDao.MaterialCriteria criteria);

    OrcaMasterDao.ListSearchResult<OrcaMasterDao.KensaSortRecord> searchKensaSort(OrcaMasterDao.KensaSortCriteria criteria);

    EtensuDao.EtensuSearchResult searchEtensu(EtensuDao.EtensuSearchCriteria criteria);
}

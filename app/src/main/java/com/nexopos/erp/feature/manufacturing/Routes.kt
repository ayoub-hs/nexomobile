package com.nexopos.erp.feature.manufacturing

/**
 * Manufacturing Feature Routes
 * Navigation routes for the Manufacturing feature.
 * 
 * Flow: Production List → Production Edit | BOMs List → BOM Edit
 */
object ManufacturingRoutes {
    const val PRODUCTION = "manufacturing/production"
    const val PRODUCTION_EDIT = "manufacturing/production/{id}"
    const val BOMS = "manufacturing/boms"
    const val BOM_EDIT = "manufacturing/boms/{id}"
    
    /**
     * Create route for production order edit screen
     */
    fun productionEdit(id: Long) = "manufacturing/production/$id"
    
    /**
     * Create route for BOM edit screen
     */
    fun bomEdit(id: Long) = "manufacturing/boms/$id"
    
    // Legacy routes for backward compatibility during migration
    @Deprecated("Use PRODUCTION instead")
    const val PRODUCTION_CREATE = "manufacturing/create"
    
    @Deprecated("Use PRODUCTION_EDIT instead")
    const val PRODUCTION_DETAIL = "manufacturing/{id}"
    
    @Deprecated("Use BOM_EDIT instead")
    const val BOM_DETAIL = "manufacturing/boms/{id}"
    
    @Deprecated("Use bomEdit() function instead")
    const val BOM_CREATE = "manufacturing/boms/create"
}

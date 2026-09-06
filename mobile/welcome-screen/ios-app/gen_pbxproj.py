# -*- coding: utf-8 -*-
"""Generate a minimal, standard ShowUpWelcome.xcodeproj/project.pbxproj.

Run from this directory:

    python gen_pbxproj.py

Sources and fonts are DISCOVERED from disk rather than listed here. They used to be a hardcoded
list, which meant a new .swift file compiled fine locally and then silently never reached the Xcode
target — the symptom is "cannot find X in scope" for a type that plainly exists. Scanning removes
that failure mode: if the file is in ShowUpWelcome/, it is in the target.

ShowUpWelcomeApp.swift is forced first only so the diff stays stable; Swift itself does not care
about file order.

The unit-test bundle is discovered the same way, from ShowUpWelcomeTests/. It used not to exist at
all, which is how eight written tests sat unrun for a week: the file was in the repo but in no
target, so `cmd-U` had nothing to run and said so only if you looked.

The scheme (xcshareddata/xcschemes/ShowUpWelcome.xcscheme) is NOT generated, and it references two
ids from here by hand: FEED...0002 is the app target and FEED...0003 the test bundle. They are the
first uids handed out for exactly that reason -- everything else shifts when a file is added.
"""
import io, os

_n = [0]
def uid():
    _n[0] += 1
    return "FEED%020X" % _n[0]          # 24 hex chars, unique and deterministic

HERE = os.path.dirname(os.path.abspath(__file__))
SRCDIR = os.path.join(HERE, "ShowUpWelcome")
FONTDIR = os.path.join(SRCDIR, "Fonts")
TESTDIR = os.path.join(HERE, "ShowUpWelcomeTests")

FIRST = "ShowUpWelcomeApp.swift"
_found = sorted(f for f in os.listdir(SRCDIR) if f.endswith(".swift"))
sources = ([FIRST] if FIRST in _found else []) + [f for f in _found if f != FIRST]
fonts = sorted(f for f in os.listdir(FONTDIR) if f.lower().endswith((".ttf", ".otf")))
tests = sorted(f for f in os.listdir(TESTDIR) if f.endswith(".swift")) if os.path.isdir(TESTDIR) else []

if not sources:
    raise SystemExit("no .swift files found in " + SRCDIR)
print("sources: %d, fonts: %d, tests: %d" % (len(sources), len(fonts), len(tests)))

PROJECT, TARGET, TESTTARGET, ROOTGRP, APPGRP, FONTGRP, PRODGRP = (uid() for _ in range(7))
APPREF, PLISTREF, ASSETREF = uid(), uid(), uid()
SRCPHASE, FRMPHASE, RESPHASE = uid(), uid(), uid()
CFG_PROJ, CFG_TGT = uid(), uid()
CFG_PD, CFG_PR, CFG_TD, CFG_TR = (uid() for _ in range(4))
ASSETBUILD = uid()
FLAGSREF, FLAGSBUILD = uid(), uid()
src_ref = {f: uid() for f in sources}
src_bld = {f: uid() for f in sources}
fnt_ref = {f: uid() for f in fonts}
fnt_bld = {f: uid() for f in fonts}
TESTGRP, TESTPRODREF = uid(), uid()
TESTSRCPHASE, TESTFRMPHASE, TESTRESPHASE = uid(), uid(), uid()
CFG_TESTLIST, CFG_TESTD, CFG_TESTR = uid(), uid(), uid()
TESTPROXY, TESTDEP = uid(), uid()
PKGREF, PKGPROD, PKGBUILD, PKGTESTPROD, PKGTESTBUILD = (uid() for _ in range(5))
# The generated API client, ShowUpAPI. A LOCAL package reference rather than a remote one, and a
# package rather than a build plugin on this target -- see ShowUpAPI/Package.swift for why.
APIPKGREF, APIPROD, APIBUILD, APITESTPROD, APITESTBUILD = (uid() for _ in range(5))
# Crash reporting. A remote package, pinned to a major version -- see the note by its
# XCRemoteSwiftPackageReference below.
SENREF, SENPROD, SENBUILD, SENTESTPROD, SENTESTBUILD = (uid() for _ in range(5))
# ShowUpAPI's own dependencies, which the APP has to link too.
#
# A SwiftPM library product is built statically here, so ShowUpAPI's object code lands inside the
# app binary -- and its dependencies do not come with it. Declaring only ShowUpAPI links the wrapper
# and leaves every OpenAPIRuntime and OpenAPIURLSession symbol undefined at link time.
#
# Xcode adds these automatically when a package is added through its UI. This file is written by
# hand, so they are written by hand.
RTREF, RTPROD, RTBUILD = (uid() for _ in range(3))
USREF, USPROD, USBUILD = (uid() for _ in range(3))
HTREF, HTPROD, HTBUILD = (uid() for _ in range(3))
# ...and again for the TEST bundle, which links separately from the app.
#
# APIAccessTests calls a generated operation, and the default argument on its Headers initialiser
# inlines OpenAPIRuntime.AcceptHeaderContentType into the CALLER -- so the test bundle needs these
# symbols whether or not the app already has them. Missing this produced a link failure whose
# undefined symbols named APIAccessTests.o while every declaration looked correct on the app.
RTTESTPROD, RTTESTBUILD = uid(), uid()
USTESTPROD, USTESTBUILD = uid(), uid()
HTTESTPROD, HTTESTBUILD = uid(), uid()
tst_ref = {f: uid() for f in tests}
tst_bld = {f: uid() for f in tests}

T = "\t"
L = []
w = L.append

w("// !$*UTF8*$!")
w("{")
w(T + "archiveVersion = 1;")
w(T + "classes = {")
w(T + "};")
w(T + "objectVersion = 56;")
w(T + "objects = {")

w("")
w("/* Begin PBXBuildFile section */")
for f in sources:
    w(T*2 + "%s /* %s in Sources */ = {isa = PBXBuildFile; fileRef = %s /* %s */; };"
      % (src_bld[f], f, src_ref[f], f))
w(T*2 + "%s /* Assets.xcassets in Resources */ = {isa = PBXBuildFile; fileRef = %s /* Assets.xcassets */; };"
  % (ASSETBUILD, ASSETREF))
for f in fonts:
    w(T*2 + "%s /* %s in Resources */ = {isa = PBXBuildFile; fileRef = %s /* %s */; };"
      % (fnt_bld[f], f, fnt_ref[f], f))
w(T*2 + '%s /* Flags in Resources */ = {isa = PBXBuildFile; fileRef = %s /* Flags */; };'
  % (FLAGSBUILD, FLAGSREF))
for f in tests:
    w(T*2 + "%s /* %s in Sources */ = {isa = PBXBuildFile; fileRef = %s /* %s */; };"
      % (tst_bld[f], f, tst_ref[f], f))
w(T*2 + "%s /* PhoneNumberKit in Frameworks */ = {isa = PBXBuildFile; productRef = %s /* PhoneNumberKit */; };"
  % (PKGBUILD, PKGPROD))
w(T*2 + "%s /* ShowUpAPI in Frameworks */ = {isa = PBXBuildFile; productRef = %s /* ShowUpAPI */; };"
  % (APIBUILD, APIPROD))
w(T*2 + "%s /* Sentry in Frameworks */ = {isa = PBXBuildFile; productRef = %s /* Sentry */; };"
  % (SENBUILD, SENPROD))
for _b, _p, _name in ((RTBUILD, RTPROD, "OpenAPIRuntime"),
                   (USBUILD, USPROD, "OpenAPIURLSession"),
                   (HTBUILD, HTPROD, "HTTPTypes")):
    w(T*2 + "%s /* %s in Frameworks */ = {isa = PBXBuildFile; productRef = %s /* %s */; };"
      % (_b, _name, _p, _name))
if tests:
    w(T*2 + "%s /* PhoneNumberKit in Frameworks */ = {isa = PBXBuildFile; productRef = %s /* PhoneNumberKit */; };"
      % (PKGTESTBUILD, PKGTESTPROD))
    w(T*2 + "%s /* ShowUpAPI in Frameworks */ = {isa = PBXBuildFile; productRef = %s /* ShowUpAPI */; };"
      % (APITESTBUILD, APITESTPROD))
    w(T*2 + "%s /* Sentry in Frameworks */ = {isa = PBXBuildFile; productRef = %s /* Sentry */; };"
      % (SENTESTBUILD, SENTESTPROD))
    for _b, _p, _name in ((RTTESTBUILD, RTTESTPROD, "OpenAPIRuntime"),
                          (USTESTBUILD, USTESTPROD, "OpenAPIURLSession"),
                          (HTTESTBUILD, HTTESTPROD, "HTTPTypes")):
        w(T*2 + "%s /* %s in Frameworks */ = {isa = PBXBuildFile; productRef = %s /* %s */; };"
          % (_b, _name, _p, _name))
w("/* End PBXBuildFile section */")

w("")
w("/* Begin PBXFileReference section */")
w(T*2 + "%s /* ShowUpWelcome.app */ = {isa = PBXFileReference; explicitFileType = wrapper.application; "
        "includeInIndex = 0; path = ShowUpWelcome.app; sourceTree = BUILT_PRODUCTS_DIR; };" % APPREF)
for f in sources:
    w(T*2 + '%s /* %s */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = %s; sourceTree = "<group>"; };'
      % (src_ref[f], f, f))
w(T*2 + '%s /* Info.plist */ = {isa = PBXFileReference; lastKnownFileType = text.plist.xml; path = Info.plist; sourceTree = "<group>"; };' % PLISTREF)
w(T*2 + '%s /* Assets.xcassets */ = {isa = PBXFileReference; lastKnownFileType = folder.assetcatalog; path = Assets.xcassets; sourceTree = "<group>"; };' % ASSETREF)
# lastKnownFileType = folder makes this a FOLDER reference: Xcode copies the directory into the
# bundle as-is, so the 257 flag files need no entries of their own and adding one needs no change
# here. They are read at runtime by name -- Flags/DE.png -- in CountryPicker.swift.
w(T*2 + '%s /* Flags */ = {isa = PBXFileReference; lastKnownFileType = folder; path = Flags; sourceTree = "<group>"; };' % FLAGSREF)
for f in fonts:
    w(T*2 + '%s /* %s */ = {isa = PBXFileReference; lastKnownFileType = file; path = %s; sourceTree = "<group>"; };'
      % (fnt_ref[f], f, f))
for f in tests:
    w(T*2 + '%s /* %s */ = {isa = PBXFileReference; lastKnownFileType = sourcecode.swift; path = %s; sourceTree = "<group>"; };'
      % (tst_ref[f], f, f))
w(T*2 + '%s /* ShowUpWelcomeTests.xctest */ = {isa = PBXFileReference; explicitFileType = wrapper.cfbundle; '
        'includeInIndex = 0; path = ShowUpWelcomeTests.xctest; sourceTree = BUILT_PRODUCTS_DIR; };' % TESTPRODREF)
w("/* End PBXFileReference section */")

w("")
w("/* Begin PBXFrameworksBuildPhase section */")
w(T*2 + "%s /* Frameworks */ = {" % FRMPHASE)
w(T*3 + "isa = PBXFrameworksBuildPhase;")
w(T*3 + "buildActionMask = 2147483647;")
w(T*3 + "files = (")
w(T*4 + "%s /* PhoneNumberKit in Frameworks */," % PKGBUILD)
w(T*4 + "%s /* ShowUpAPI in Frameworks */," % APIBUILD)
w(T*4 + "%s /* Sentry in Frameworks */," % SENBUILD)
for _b, _name in ((RTBUILD, "OpenAPIRuntime"), (USBUILD, "OpenAPIURLSession"), (HTBUILD, "HTTPTypes")):
    w(T*4 + "%s /* %s in Frameworks */," % (_b, _name))
w(T*3 + ");")
w(T*3 + "runOnlyForDeploymentPostprocessing = 0;")
w(T*2 + "};")
w(T*2 + "%s /* Frameworks */ = {" % TESTFRMPHASE)
w(T*3 + "isa = PBXFrameworksBuildPhase;")
w(T*3 + "buildActionMask = 2147483647;")
w(T*3 + "files = (")
w(T*4 + "%s /* PhoneNumberKit in Frameworks */," % PKGTESTBUILD)
w(T*4 + "%s /* ShowUpAPI in Frameworks */," % APITESTBUILD)
w(T*4 + "%s /* Sentry in Frameworks */," % SENTESTBUILD)
for _b, _name in ((RTTESTBUILD, "OpenAPIRuntime"), (USTESTBUILD, "OpenAPIURLSession"),
                  (HTTESTBUILD, "HTTPTypes")):
    w(T*4 + "%s /* %s in Frameworks */," % (_b, _name))
w(T*3 + ");")
w(T*3 + "runOnlyForDeploymentPostprocessing = 0;")
w(T*2 + "};")
w("/* End PBXFrameworksBuildPhase section */")

w("")
w("/* Begin PBXGroup section */")
w(T*2 + "%s = {" % ROOTGRP)
w(T*3 + "isa = PBXGroup;")
w(T*3 + "children = (")
w(T*4 + "%s /* ShowUpWelcome */," % APPGRP)
if tests:
    w(T*4 + "%s /* ShowUpWelcomeTests */," % TESTGRP)
w(T*4 + "%s /* Products */," % PRODGRP)
w(T*3 + ");")
w(T*3 + 'sourceTree = "<group>";')
w(T*2 + "};")

w(T*2 + "%s /* ShowUpWelcome */ = {" % APPGRP)
w(T*3 + "isa = PBXGroup;")
w(T*3 + "children = (")
for f in sources:
    w(T*4 + "%s /* %s */," % (src_ref[f], f))
w(T*4 + "%s /* Assets.xcassets */," % ASSETREF)
w(T*4 + "%s /* Flags */," % FLAGSREF)
w(T*4 + "%s /* Fonts */," % FONTGRP)
w(T*4 + "%s /* Info.plist */," % PLISTREF)
w(T*3 + ");")
w(T*3 + "path = ShowUpWelcome;")
w(T*3 + 'sourceTree = "<group>";')
w(T*2 + "};")

w(T*2 + "%s /* Fonts */ = {" % FONTGRP)
w(T*3 + "isa = PBXGroup;")
w(T*3 + "children = (")
for f in fonts:
    w(T*4 + "%s /* %s */," % (fnt_ref[f], f))
w(T*3 + ");")
w(T*3 + "path = Fonts;")
w(T*3 + 'sourceTree = "<group>";')
w(T*2 + "};")

if tests:
    w(T*2 + "%s /* ShowUpWelcomeTests */ = {" % TESTGRP)
    w(T*3 + "isa = PBXGroup;")
    w(T*3 + "children = (")
    for f in tests:
        w(T*4 + "%s /* %s */," % (tst_ref[f], f))
    w(T*3 + ");")
    w(T*3 + "path = ShowUpWelcomeTests;")
    w(T*3 + 'sourceTree = "<group>";')
    w(T*2 + "};")

w(T*2 + "%s /* Products */ = {" % PRODGRP)
w(T*3 + "isa = PBXGroup;")
w(T*3 + "children = (")
w(T*4 + "%s /* ShowUpWelcome.app */," % APPREF)
if tests:
    w(T*4 + "%s /* ShowUpWelcomeTests.xctest */," % TESTPRODREF)
w(T*3 + ");")
w(T*3 + "name = Products;")
w(T*3 + 'sourceTree = "<group>";')
w(T*2 + "};")
w("/* End PBXGroup section */")

w("")
w("/* Begin PBXNativeTarget section */")
w(T*2 + "%s /* ShowUpWelcome */ = {" % TARGET)
w(T*3 + "isa = PBXNativeTarget;")
w(T*3 + 'buildConfigurationList = %s /* Build configuration list for PBXNativeTarget "ShowUpWelcome" */;' % CFG_TGT)
w(T*3 + "buildPhases = (")
w(T*4 + "%s /* Sources */," % SRCPHASE)
w(T*4 + "%s /* Frameworks */," % FRMPHASE)
w(T*4 + "%s /* Resources */," % RESPHASE)
w(T*3 + ");")
w(T*3 + "buildRules = (")
w(T*3 + ");")
w(T*3 + "dependencies = (")
w(T*3 + ");")
w(T*3 + "name = ShowUpWelcome;")
w(T*3 + "productName = ShowUpWelcome;")
w(T*3 + "packageProductDependencies = (")
w(T*4 + "%s /* PhoneNumberKit */," % PKGPROD)
w(T*4 + "%s /* ShowUpAPI */," % APIPROD)
w(T*4 + "%s /* Sentry */," % SENPROD)
for _p, _name in ((RTPROD, "OpenAPIRuntime"), (USPROD, "OpenAPIURLSession"), (HTPROD, "HTTPTypes")):
    w(T*4 + "%s /* %s */," % (_p, _name))
w(T*3 + ");")
w(T*3 + "productReference = %s /* ShowUpWelcome.app */;" % APPREF)
w(T*3 + 'productType = "com.apple.product-type.application";')
w(T*2 + "};")
if tests:
    w(T*2 + "%s /* ShowUpWelcomeTests */ = {" % TESTTARGET)
    w(T*3 + "isa = PBXNativeTarget;")
    w(T*3 + 'buildConfigurationList = %s /* Build configuration list for PBXNativeTarget "ShowUpWelcomeTests" */;' % CFG_TESTLIST)
    w(T*3 + "buildPhases = (")
    w(T*4 + "%s /* Sources */," % TESTSRCPHASE)
    w(T*4 + "%s /* Frameworks */," % TESTFRMPHASE)
    w(T*4 + "%s /* Resources */," % TESTRESPHASE)
    w(T*3 + ");")
    w(T*3 + "buildRules = (")
    w(T*3 + ");")
    w(T*3 + "dependencies = (")
    w(T*4 + "%s /* PBXTargetDependency */," % TESTDEP)
    w(T*3 + ");")
    w(T*3 + "name = ShowUpWelcomeTests;")
    w(T*3 + "productName = ShowUpWelcomeTests;")
    w(T*3 + "packageProductDependencies = (")
    w(T*4 + "%s /* PhoneNumberKit */," % PKGTESTPROD)
    w(T*4 + "%s /* ShowUpAPI */," % APITESTPROD)
    w(T*4 + "%s /* Sentry */," % SENTESTPROD)
    for _p, _name in ((RTTESTPROD, "OpenAPIRuntime"), (USTESTPROD, "OpenAPIURLSession"),
                      (HTTESTPROD, "HTTPTypes")):
        w(T*4 + "%s /* %s */," % (_p, _name))
    w(T*3 + ");")
    w(T*3 + "productReference = %s /* ShowUpWelcomeTests.xctest */;" % TESTPRODREF)
    w(T*3 + 'productType = "com.apple.product-type.bundle.unit-test";')
    w(T*2 + "};")
w("/* End PBXNativeTarget section */")

if tests:
    w("")
    w("/* Begin PBXContainerItemProxy section */")
    w(T*2 + "%s /* PBXContainerItemProxy */ = {" % TESTPROXY)
    w(T*3 + "isa = PBXContainerItemProxy;")
    w(T*3 + "containerPortal = %s /* Project object */;" % PROJECT)
    w(T*3 + "proxyType = 1;")
    w(T*3 + "remoteGlobalIDString = %s;" % TARGET)
    w(T*3 + "remoteInfo = ShowUpWelcome;")
    w(T*2 + "};")
    w("/* End PBXContainerItemProxy section */")

    w("")
    w("/* Begin PBXTargetDependency section */")
    w(T*2 + "%s /* PBXTargetDependency */ = {" % TESTDEP)
    w(T*3 + "isa = PBXTargetDependency;")
    w(T*3 + "target = %s /* ShowUpWelcome */;" % TARGET)
    w(T*3 + "targetProxy = %s /* PBXContainerItemProxy */;" % TESTPROXY)
    w(T*2 + "};")
    w("/* End PBXTargetDependency section */")

w("")
w("/* Begin PBXProject section */")
w(T*2 + "%s /* Project object */ = {" % PROJECT)
w(T*3 + "isa = PBXProject;")
w(T*3 + "attributes = {")
w(T*4 + "BuildIndependentTargetsInParallel = 1;")
w(T*4 + "LastSwiftUpdateCheck = 1500;")
w(T*4 + "LastUpgradeCheck = 1500;")
w(T*4 + "TargetAttributes = {")
w(T*5 + "%s = {" % TARGET)
w(T*6 + "CreatedOnToolsVersion = 15.0;")
w(T*5 + "};")
if tests:
    w(T*5 + "%s = {" % TESTTARGET)
    w(T*6 + "CreatedOnToolsVersion = 15.0;")
    w(T*6 + "TestTargetID = %s;" % TARGET)
    w(T*5 + "};")
w(T*4 + "};")
w(T*3 + "};")
w(T*3 + 'buildConfigurationList = %s /* Build configuration list for PBXProject "ShowUpWelcome" */;' % CFG_PROJ)
w(T*3 + 'compatibilityVersion = "Xcode 14.0";')
w(T*3 + "developmentRegion = en;")
w(T*3 + "hasScannedForEncodings = 0;")
w(T*3 + "knownRegions = (")
w(T*4 + "en,")
w(T*4 + "Base,")
w(T*3 + ");")
w(T*3 + "mainGroup = %s;" % ROOTGRP)
w(T*3 + "packageReferences = (")
w(T*4 + '%s /* XCRemoteSwiftPackageReference "PhoneNumberKit" */,' % PKGREF)
w(T*4 + '%s /* XCLocalSwiftPackageReference "ShowUpAPI" */,' % APIPKGREF)
w(T*4 + '%s /* XCRemoteSwiftPackageReference "sentry-cocoa" */,' % SENREF)
for _r, _name in ((RTREF, "swift-openapi-runtime"), (USREF, "swift-openapi-urlsession"),
               (HTREF, "swift-http-types")):
    w(T*4 + '%s /* XCRemoteSwiftPackageReference "%s" */,' % (_r, _name))
w(T*3 + ");")
w(T*3 + "productRefGroup = %s /* Products */;" % PRODGRP)
w(T*3 + 'projectDirPath = "";')
w(T*3 + 'projectRoot = "";')
w(T*3 + "targets = (")
w(T*4 + "%s /* ShowUpWelcome */," % TARGET)
if tests:
    w(T*4 + "%s /* ShowUpWelcomeTests */," % TESTTARGET)
w(T*3 + ");")
w(T*2 + "};")
w("/* End PBXProject section */")

w("")
w("/* Begin PBXResourcesBuildPhase section */")
w(T*2 + "%s /* Resources */ = {" % RESPHASE)
w(T*3 + "isa = PBXResourcesBuildPhase;")
w(T*3 + "buildActionMask = 2147483647;")
w(T*3 + "files = (")
w(T*4 + "%s /* Assets.xcassets in Resources */," % ASSETBUILD)
# The flag artwork, as a FOLDER reference: 257 files copied wholesale rather than 257 entries
# here, so adding or removing a flag needs no change to the project file.
w(T*4 + "%s /* Flags in Resources */," % FLAGSBUILD)
for f in fonts:
    w(T*4 + "%s /* %s in Resources */," % (fnt_bld[f], f))
w(T*3 + ");")
w(T*3 + "runOnlyForDeploymentPostprocessing = 0;")
w(T*2 + "};")
if tests:
    w(T*2 + "%s /* Resources */ = {" % TESTRESPHASE)
    w(T*3 + "isa = PBXResourcesBuildPhase;")
    w(T*3 + "buildActionMask = 2147483647;")
    w(T*3 + "files = (")
    w(T*3 + ");")
    w(T*3 + "runOnlyForDeploymentPostprocessing = 0;")
    w(T*2 + "};")
w("/* End PBXResourcesBuildPhase section */")

w("")
w("/* Begin PBXSourcesBuildPhase section */")
w(T*2 + "%s /* Sources */ = {" % SRCPHASE)
w(T*3 + "isa = PBXSourcesBuildPhase;")
w(T*3 + "buildActionMask = 2147483647;")
w(T*3 + "files = (")
for f in sources:
    w(T*4 + "%s /* %s in Sources */," % (src_bld[f], f))
w(T*3 + ");")
w(T*3 + "runOnlyForDeploymentPostprocessing = 0;")
w(T*2 + "};")
if tests:
    w(T*2 + "%s /* Sources */ = {" % TESTSRCPHASE)
    w(T*3 + "isa = PBXSourcesBuildPhase;")
    w(T*3 + "buildActionMask = 2147483647;")
    w(T*3 + "files = (")
    for f in tests:
        w(T*4 + "%s /* %s in Sources */," % (tst_bld[f], f))
    w(T*3 + ");")
    w(T*3 + "runOnlyForDeploymentPostprocessing = 0;")
    w(T*2 + "};")
w("/* End PBXSourcesBuildPhase section */")

PROJ_COMMON = [
    "ALWAYS_SEARCH_USER_PATHS = NO;",
    "CLANG_ANALYZER_NONNULL = YES;",
    "CLANG_ENABLE_MODULES = YES;",
    "CLANG_ENABLE_OBJC_ARC = YES;",
    "CLANG_WARN_BOOL_CONVERSION = YES;",
    "CLANG_WARN_DOCUMENTATION_COMMENTS = YES;",
    "CLANG_WARN_EMPTY_BODY = YES;",
    "CLANG_WARN_UNREACHABLE_CODE = YES;",
    "COPY_PHASE_STRIP = NO;",
    "ENABLE_STRICT_OBJC_MSGSEND = YES;",
    "GCC_C_LANGUAGE_STANDARD = gnu17;",
    "GCC_NO_COMMON_BLOCKS = YES;",
    "GCC_WARN_UNUSED_VARIABLE = YES;",
    # 17.0, confirmed by the product side on 2026-09-01. It drops iPhone X and older, and in
    # exchange removes a whole class of bug this project hit twice: valid Swift that needs a newer
    # OS than the target claims. Two availability forks were deleted outright when it moved.
    "IPHONEOS_DEPLOYMENT_TARGET = 17.0;",
    "SDKROOT = iphoneos;",
]
DEBUG_EXTRA = [
    "DEBUG_INFORMATION_FORMAT = dwarf;",
    "ENABLE_TESTABILITY = YES;",
    "GCC_OPTIMIZATION_LEVEL = 0;",
    "MTL_ENABLE_DEBUG_INFO = INCLUDE_SOURCE;",
    "ONLY_ACTIVE_ARCH = YES;",
    'SWIFT_ACTIVE_COMPILATION_CONDITIONS = "DEBUG $(inherited)";',
    'SWIFT_OPTIMIZATION_LEVEL = "-Onone";',
]
RELEASE_EXTRA = [
    'DEBUG_INFORMATION_FORMAT = "dwarf-with-dsym";',
    "ENABLE_NS_ASSERTIONS = NO;",
    "MTL_ENABLE_DEBUG_INFO = NO;",
    "SWIFT_COMPILATION_MODE = wholemodule;",
    "VALIDATE_PRODUCT = YES;",
]

def build_cfg(cfg_id, name, settings):
    w(T*2 + "%s /* %s */ = {" % (cfg_id, name))
    w(T*3 + "isa = XCBuildConfiguration;")
    w(T*3 + "buildSettings = {")
    for line in settings:
        w(T*4 + line)
    w(T*3 + "};")
    w(T*3 + "name = %s;" % name)
    w(T*2 + "};")

TGT_COMMON = [
    "ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon;",
    "ASSETCATALOG_COMPILER_GLOBAL_ACCENT_COLOR_NAME = AccentColor;",
    "CODE_SIGN_STYLE = Automatic;",
    "CURRENT_PROJECT_VERSION = 1;",
    "ENABLE_PREVIEWS = YES;",
    "GENERATE_INFOPLIST_FILE = NO;",
    "INFOPLIST_FILE = ShowUpWelcome/Info.plist;",
    "LD_RUNPATH_SEARCH_PATHS = (",
    '\t"$(inherited)",',
    '\t"@executable_path/Frameworks",',
    ");",
    "MARKETING_VERSION = 0.1;",
    "PRODUCT_BUNDLE_IDENTIFIER = org.loveiq.showup.welcomepreview;",
    'PRODUCT_NAME = "$(TARGET_NAME)";',
    "SWIFT_EMIT_LOC_STRINGS = YES;",
    # Step one of the Swift 6 migration, and the only step that can be taken without a compiler.
    #
    # `complete` reports every data-race problem Swift 6 will REFUSE to build, but reports them as
    # warnings, because the language mode below is still 5.0. So this cannot break the build: it
    # turns an invisible future problem into a visible present list.
    #
    # Step two is fixing what it reports, and that needs someone who can see the messages. Doing it
    # blind would mean changing code to satisfy errors nobody has read.
    #
    # When the list is clear, change SWIFT_VERSION to 6.0 and the warnings become the guarantee.
    # Worth doing while this target has a dozen files rather than a hundred.
    "SWIFT_STRICT_CONCURRENCY = complete;",
    "SWIFT_VERSION = 5.0;",
    "TARGETED_DEVICE_FAMILY = 1;",
]

# The test bundle is hosted by the app: BUNDLE_LOADER/TEST_HOST are what let `@testable import
# ShowUpWelcome` see internal symbols, which every one of these tests needs -- outcomeOf and
# showsTutorial are internal, not public.
TEST_COMMON = [
    'BUNDLE_LOADER = "$(TEST_HOST)";',
    "CODE_SIGN_STYLE = Automatic;",
    "CURRENT_PROJECT_VERSION = 1;",
    "GENERATE_INFOPLIST_FILE = YES;",
    # No IPHONEOS_DEPLOYMENT_TARGET here on purpose: it inherits the project's, so the test bundle
    # cannot drift away from the app it hosts. It was pinned at 16.0 and was already stale by the
    # time it was written -- the minimums moved to 17 in the same week.
    "MARKETING_VERSION = 0.1;",
    "PRODUCT_BUNDLE_IDENTIFIER = org.loveiq.showup.welcomepreview.tests;",
    'PRODUCT_NAME = "$(TARGET_NAME)";',
    "SWIFT_EMIT_LOC_STRINGS = NO;",
    "SWIFT_STRICT_CONCURRENCY = complete;",
    "SWIFT_VERSION = 5.0;",
    "TARGETED_DEVICE_FAMILY = 1;",
    'TEST_HOST = "$(BUILT_PRODUCTS_DIR)/ShowUpWelcome.app/$(BUNDLE_EXECUTABLE_FOLDER_PATH)/ShowUpWelcome";',
]

w("")
w("/* Begin XCBuildConfiguration section */")
build_cfg(CFG_PD, "Debug", PROJ_COMMON + DEBUG_EXTRA)
build_cfg(CFG_PR, "Release", PROJ_COMMON + RELEASE_EXTRA)
build_cfg(CFG_TD, "Debug", TGT_COMMON)
build_cfg(CFG_TR, "Release", TGT_COMMON)
if tests:
    build_cfg(CFG_TESTD, "Debug", TEST_COMMON)
    build_cfg(CFG_TESTR, "Release", TEST_COMMON)
w("/* End XCBuildConfiguration section */")

w("")
w("/* Begin XCConfigurationList section */")
_cfglists = [
    (CFG_PROJ, 'Build configuration list for PBXProject "ShowUpWelcome"', CFG_PD, CFG_PR),
    (CFG_TGT, 'Build configuration list for PBXNativeTarget "ShowUpWelcome"', CFG_TD, CFG_TR),
]
if tests:
    _cfglists.append(
        (CFG_TESTLIST, 'Build configuration list for PBXNativeTarget "ShowUpWelcomeTests"',
         CFG_TESTD, CFG_TESTR))
for cfg_id, label, d, r in _cfglists:
    w(T*2 + "%s /* %s */ = {" % (cfg_id, label))
    w(T*3 + "isa = XCConfigurationList;")
    w(T*3 + "buildConfigurations = (")
    w(T*4 + "%s /* Debug */," % d)
    w(T*4 + "%s /* Release */," % r)
    w(T*3 + ");")
    w(T*3 + "defaultConfigurationIsVisible = 0;")
    w(T*3 + "defaultConfigurationName = Release;")
    w(T*2 + "};")
w("/* End XCConfigurationList section */")

# ── the one dependency ─────────────────────────────────────────────────────
#
# PhoneNumberKit is the Swift port of the same metadata Android already uses through
# libphonenumber, from the maintained org -- marmelroy/PhoneNumberKit is archived and says so on
# every deprecated symbol. Apache 2.0, ships inside the app, no server and nothing metered.
#
# It replaces 35 hand-written countries with every country there is, and hand-written length rules
# with rules that know a mobile from a landline. Pinned to a major version: the metadata inside it
# is updated regularly and those updates are the point, but an API break should not arrive silently.
w("")
w("/* Begin XCRemoteSwiftPackageReference section */")
w(T*2 + '%s /* XCRemoteSwiftPackageReference "PhoneNumberKit" */ = {' % PKGREF)
w(T*3 + "isa = XCRemoteSwiftPackageReference;")
w(T*3 + 'repositoryURL = "https://github.com/PhoneNumberKit/PhoneNumberKit.git";')
w(T*3 + "requirement = {")
w(T*4 + "kind = upToNextMajorVersion;")
w(T*4 + "minimumVersion = 5.0.8;")
w(T*3 + "};")
w(T*2 + "};")
w(T*2 + '%s /* XCRemoteSwiftPackageReference "sentry-cocoa" */ = {' % SENREF)
w(T*3 + "isa = XCRemoteSwiftPackageReference;")
w(T*3 + 'repositoryURL = "https://github.com/getsentry/sentry-cocoa.git";')
w(T*3 + "requirement = {")
w(T*4 + "kind = upToNextMajorVersion;")
w(T*4 + "minimumVersion = 8.44.0;")
w(T*3 + "};")
w(T*2 + "};")
for _r, _url, _min in (
    (RTREF, "https://github.com/apple/swift-openapi-runtime", "1.5.0"),
    (USREF, "https://github.com/apple/swift-openapi-urlsession", "1.0.2"),
    (HTREF, "https://github.com/apple/swift-http-types", "1.3.0"),
):
    w(T*2 + '%s /* XCRemoteSwiftPackageReference "%s" */ = {' % (_r, _url.rsplit("/", 1)[-1]))
    w(T*3 + "isa = XCRemoteSwiftPackageReference;")
    w(T*3 + 'repositoryURL = "%s";' % _url)
    w(T*3 + "requirement = {")
    w(T*4 + "kind = upToNextMajorVersion;")
    w(T*4 + "minimumVersion = %s;" % _min)
    w(T*3 + "};")
    w(T*2 + "};")
w("/* End XCRemoteSwiftPackageReference section */")

# The generated API client, as a LOCAL package in a sibling directory.
#
# Local rather than remote because it lives in this repository, and a package rather than a build
# plugin attached to this target because plugin invocation is the least documented corner of the
# pbxproj format -- see ShowUpAPI/Package.swift. A local reference is three lines and stable.
#
# `relativePath` is resolved from the directory containing the .xcodeproj, which is this one.
#
# XCLocalSwiftPackageReference needs objectVersion 55 or newer. This file writes 56, so it is fine;
# lowering that would break this silently, with Xcode reporting a missing package rather than an
# unsupported format.
w("")
w("/* Begin XCLocalSwiftPackageReference section */")
w(T*2 + '%s /* XCLocalSwiftPackageReference "ShowUpAPI" */ = {' % APIPKGREF)
w(T*3 + "isa = XCLocalSwiftPackageReference;")
w(T*3 + "relativePath = ShowUpAPI;")
w(T*2 + "};")
w("/* End XCLocalSwiftPackageReference section */")

w("")
w("/* Begin XCSwiftPackageProductDependency section */")
w(T*2 + "%s /* PhoneNumberKit */ = {" % PKGPROD)
w(T*3 + "isa = XCSwiftPackageProductDependency;")
w(T*3 + 'package = %s /* XCRemoteSwiftPackageReference "PhoneNumberKit" */;' % PKGREF)
w(T*3 + "productName = PhoneNumberKit;")
w(T*2 + "};")
# NOTE the missing `package = ...` line below, and that it is not an omission. A product from a
# LOCAL package is identified by productName alone; adding a package reference here is what Xcode
# does for remote packages only, and including it for a local one makes the project fail to open.
w(T*2 + "%s /* ShowUpAPI */ = {" % APIPROD)
w(T*3 + "isa = XCSwiftPackageProductDependency;")
w(T*3 + "productName = ShowUpAPI;")
w(T*2 + "};")
# Sentry is REMOTE, so unlike ShowUpAPI above it does carry a `package` reference.
w(T*2 + "%s /* Sentry */ = {" % SENPROD)
w(T*3 + "isa = XCSwiftPackageProductDependency;")
w(T*3 + 'package = %s /* XCRemoteSwiftPackageReference "sentry-cocoa" */;' % SENREF)
w(T*3 + "productName = Sentry;")
w(T*2 + "};")
for _p, _r, _repo, _name in ((RTPROD, RTREF, "swift-openapi-runtime", "OpenAPIRuntime"),
                          (USPROD, USREF, "swift-openapi-urlsession", "OpenAPIURLSession"),
                          (HTPROD, HTREF, "swift-http-types", "HTTPTypes")):
    w(T*2 + "%s /* %s */ = {" % (_p, _name))
    w(T*3 + "isa = XCSwiftPackageProductDependency;")
    w(T*3 + 'package = %s /* XCRemoteSwiftPackageReference "%s" */;' % (_r, _repo))
    w(T*3 + "productName = %s;" % _name)
    w(T*2 + "};")
if tests:
    w(T*2 + "%s /* PhoneNumberKit */ = {" % PKGTESTPROD)
    w(T*3 + "isa = XCSwiftPackageProductDependency;")
    w(T*3 + 'package = %s /* XCRemoteSwiftPackageReference "PhoneNumberKit" */;' % PKGREF)
    w(T*3 + "productName = PhoneNumberKit;")
    w(T*2 + "};")
    w(T*2 + "%s /* ShowUpAPI */ = {" % APITESTPROD)
    w(T*3 + "isa = XCSwiftPackageProductDependency;")
    w(T*3 + "productName = ShowUpAPI;")
    w(T*2 + "};")
    w(T*2 + "%s /* Sentry */ = {" % SENTESTPROD)
    w(T*3 + "isa = XCSwiftPackageProductDependency;")
    w(T*3 + 'package = %s /* XCRemoteSwiftPackageReference "sentry-cocoa" */;' % SENREF)
    w(T*3 + "productName = Sentry;")
    w(T*2 + "};")
    for _p, _r, _repo, _name in ((RTTESTPROD, RTREF, "swift-openapi-runtime", "OpenAPIRuntime"),
                                 (USTESTPROD, USREF, "swift-openapi-urlsession", "OpenAPIURLSession"),
                                 (HTTESTPROD, HTREF, "swift-http-types", "HTTPTypes")):
        w(T*2 + "%s /* %s */ = {" % (_p, _name))
        w(T*3 + "isa = XCSwiftPackageProductDependency;")
        w(T*3 + 'package = %s /* XCRemoteSwiftPackageReference "%s" */;' % (_r, _repo))
        w(T*3 + "productName = %s;" % _name)
        w(T*2 + "};")
w("/* End XCSwiftPackageProductDependency section */")

w(T + "};")
w(T + "rootObject = %s /* Project object */;" % PROJECT)
w("}")

out = os.path.join("ShowUpWelcome.xcodeproj", "project.pbxproj")
os.makedirs("ShowUpWelcome.xcodeproj", exist_ok=True)
io.open(out, "w", encoding="utf-8", newline="\n").write("\n".join(L) + "\n")
print("wrote %s (%d bytes, %d objects)" % (out, os.path.getsize(out), _n[0]))

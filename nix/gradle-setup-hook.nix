{ makeSetupHook, gradle }:

makeSetupHook {
  name = "gradle-setup-hook";
  propagatedBuildInputs = [ gradle ];
  passthru.gradle = gradle;
} ./setup-hook.sh
